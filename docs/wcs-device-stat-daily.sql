-- =============================================================
-- WCS 设备每日任务量与耗时汇总
--   AGV 小车：源表 WHLD_WCS.WCS_SUBTASKINFOHISTORY
--   RB 机械手：源表 WHLD_WCS.WCS_TASKINFO_HISTORY + WHLD_WCS.WMS_M_FINISHEDTASK
--   STA 站台/输送线：源表 WHLD_WCS.WCS_SUBTASKINFOHISTORY（按 FINISHEDTIME 归属）
-- 口径（2026-11-06 与现场实查核对后确定）：
--   AGV 归属日期：按 UPDATETIME（任务最后更新 ≈ 完成时间）。
--     不用 CREATETIME：任务可能跨天完成，按创建日统计"昨天"会漏掉创建更早、
--     昨天才完成的任务；按完成日统计每天自然补齐，重跑 MERGE 还能修正历史漂移。
--   AGV SUM_HOURS = SUM((FINISHEDTIME - SENDTIME) * 24)，单位"小时"
--     注意：(FINISHEDTIME - SENDTIME) 在 Oracle 中是"天数"，乘 24 才是小时。
--   AGV SUM_QTY   = 任务数 COUNT(1)；PIECE_QTY = 恒 0（明细表无件数字段，已实查确认）。
--
--   RB 归属日期：按 WMS 任务全部子任务的最后完成时间 MAX(FINISHDATE)。
--   RB SUM_QTY   = 该机械手完成的 WCS 子任务数（机械手实际动作次数）
--   RB PIECE_QTY = SUM(WMS_M_FINISHEDTASK.DISASSEMBLY_NUM)。
--     ★ 1 条 WMS 任务(拆垛/拣选)对应多条 WCS 子任务，DISASSEMBLY_NUM 是 WMS 任务级的值，
--       必须先按 WMS 任务去重（内层 GROUP BY wmf.TASK_NUMBER）再求和，
--       直接按连接行 SUM 会按子任务数翻倍。
--     ★ 跨午夜完成的 WMS 任务归属到"最后完成日"（HAVING last_finish 落在统计日），
--       避免同一条任务的件数在两天各计一次。
--   RB 设备号：TARGETID 三号一组映射 RB01~RB08（5076/5078/5081→RB01 … 5139/5141/5144→RB08），
--     与 SP 设备档案（V31 迁移建的 RB01~RB08）对齐。
--   AGV 设备号：'AGV' || LPAD(AGVCARNO, 2, '0')，即 AGV01 ~ AGV40，
--     与 SP 设备档案（V30 迁移建的 AGV01~AGV40）逐一对齐。
--   ★ 只统计 AGV 和 RB，不统计 SC：WCS 库里也有 SC 堆垛机明细，但堆垛机的运行数据
--     一律以数字孪生（DT）为准。两个源都供时长会让备件寿命 HOURS 口径翻倍，
--     所以这里用 DEVICETYPE = 'AGV' 把 SC 挡在外面。将来若要改由 WCS 供堆垛机，
--     必须先停掉 DT 源，二者只能活一个。
-- 执行顺序：⓿ 探查 → ① 建表 → ② 建过程 → ③ 建定时任务 → ④（一次性）历史补录
-- 账号：WHLD_WCS
-- =============================================================

-- ---------- ⓿ 动手前先探查口径（只读，10 秒核完） ----------
-- 0.1 表里到底有哪些列（确认件数确实没有、列名拼写无误）
--     SELECT column_name, data_type FROM all_tab_columns
--     WHERE owner = 'WHLD_WCS' AND table_name = 'WCS_SUBTASKINFOHISTORY'
--     ORDER BY column_id;
--
-- 0.2 最近 30 天的 AGV 车号是否都在 1~40 之内、有没有空车号
--     （SP 设备档案建的是 AGV01~AGV40，超出范围或空号的车会落成"孤儿数据"）
--     SELECT AGVCARNO, COUNT(1) AS CNT
--     FROM WHLD_WCS.WCS_SUBTASKINFOHISTORY
--     WHERE UPDATETIME >= TRUNC(SYSDATE) - 30 AND DEVICETYPE = 'AGV'
--     GROUP BY AGVCARNO ORDER BY 2 DESC;
--
-- 0.3 TASKSTATUS 的取值：本脚本按"已完成"统计（TASKSTATUS = '1'），
--     如果 1 不是完成态、或想连未完成的一起统计，改②④里的条件。
--     注意不过滤的话：未完成任务 FINISHEDTIME 为空，SUM_HOURS 会少算但 COUNT 会计入，
--     出现"有任务数没时长"的错位口径。
--     SELECT TASKSTATUS, COUNT(1) AS CNT
--     FROM WHLD_WCS.WCS_SUBTASKINFOHISTORY
--     WHERE UPDATETIME >= TRUNC(SYSDATE) - 7
--     GROUP BY TASKSTATUS ORDER BY 2 DESC;
--
-- 0.4 按日按车核对行数（跑批后拿这条跟 DEVICE_STAT_DAILY 对账，最直观）
--     SELECT TO_CHAR(UPDATETIME,'YYYYMMDD') AS D, COUNT(DISTINCT AGVCARNO) AS 车辆数,
--            COUNT(1) AS 任务数
--     FROM WHLD_WCS.WCS_SUBTASKINFOHISTORY
--     WHERE DEVICETYPE = 'AGV' AND TASKSTATUS = '1'
--       AND UPDATETIME >= TRUNC(SYSDATE) - 7
--     GROUP BY TO_CHAR(UPDATETIME,'YYYYMMDD') ORDER BY 1;

-- ---------- ① 汇总表（首次创建） ----------
CREATE TABLE WHLD_WCS.DEVICE_STAT_DAILY (
  STAT_DATE    DATE          NOT NULL,                  -- 统计日期（当天 00:00）
  DEVICE_TYPE  VARCHAR2(32)  NOT NULL,                  -- 设备类型：AGV 小车 / RB 机械手 / STA 站台（SC 堆垛机由 DT 供数）
  DEVICE_ID    VARCHAR2(64)  NOT NULL,                  -- 设备号：AGV01~AGV40 / RB01~RB08 / STA=FROMNODE
  SUM_HOURS    NUMBER(18,2)  DEFAULT 0 NOT NULL,        -- 总耗时（小时；RB/STA 无时长口径，恒 0）
  SUM_QTY      NUMBER(18)    DEFAULT 0 NOT NULL,        -- 完成任务数（AGV=任务笔数；RB=WCS子任务数；STA=任务笔数）
  PIECE_QTY    NUMBER(18)    DEFAULT 0 NOT NULL,        -- 件数（AGV/STA 恒 0；RB=SUM(DISASSEMBLY_NUM) 按WMS任务去重）
  CREATE_TIME  DATE          DEFAULT SYSDATE NOT NULL,
  UPDATE_TIME  DATE          DEFAULT SYSDATE NOT NULL
);
COMMENT ON TABLE  WHLD_WCS.DEVICE_STAT_DAILY IS 'WCS设备每日任务量与耗时汇总(AGV小车/RB机械手/STA站台；堆垛机SC取DT源)';
COMMENT ON COLUMN WHLD_WCS.DEVICE_STAT_DAILY.STAT_DATE   IS 'AGV按UPDATETIME归属；RB按WMS任务最后完成时间归属；STA按FINISHEDTIME归属(≈完成日)';
COMMENT ON COLUMN WHLD_WCS.DEVICE_STAT_DAILY.DEVICE_TYPE IS 'AGV=AGV小车，RB=机械手，STA=站台/输送线（SC堆垛机的运行数据由数字孪生DT提供，此处不统计）';
COMMENT ON COLUMN WHLD_WCS.DEVICE_STAT_DAILY.DEVICE_ID   IS 'AGV01~AGV40；RB01~RB08（TARGETID三号一组映射）；STA=FROMNODE，均与SP设备档案对齐';
COMMENT ON COLUMN WHLD_WCS.DEVICE_STAT_DAILY.SUM_HOURS   IS 'AGV：SUM((FINISHEDTIME-SENDTIME)*24)小时；RB/STA无时长口径恒0';
COMMENT ON COLUMN WHLD_WCS.DEVICE_STAT_DAILY.SUM_QTY     IS 'AGV：完成任务数COUNT(1)；RB：WCS子任务数(机械手动作次数)；STA：完成任务笔数';
COMMENT ON COLUMN WHLD_WCS.DEVICE_STAT_DAILY.PIECE_QTY   IS 'AGV/STA恒0(明细无件数字段)；RB=SUM(DISASSEMBLY_NUM)按WMS任务去重';

ALTER TABLE WHLD_WCS.DEVICE_STAT_DAILY
  ADD CONSTRAINT PK_DEVICE_STAT_DAILY PRIMARY KEY (STAT_DATE, DEVICE_TYPE, DEVICE_ID);
-- 约束保留 SC：现在只写 AGV/RB，但将来若改用 WCS 供堆垛机数据不必改表结构
ALTER TABLE WHLD_WCS.DEVICE_STAT_DAILY
  ADD CONSTRAINT CHK_DSD_TYPE CHECK (DEVICE_TYPE IN ('SC', 'AGV', 'RB', 'STA'));

-- ---------- ①-d 表已建好、要加新设备类型时：先放宽 CHECK 约束 ----------
-- 现网表是用旧脚本建的，约束只有 ('SC','AGV')，不放开的话新类型数据 MERGE 报 ORA-02290：
--   ALTER TABLE WHLD_WCS.DEVICE_STAT_DAILY DROP CONSTRAINT CHK_DSD_TYPE;
--   ALTER TABLE WHLD_WCS.DEVICE_STAT_DAILY
--     ADD CONSTRAINT CHK_DSD_TYPE CHECK (DEVICE_TYPE IN ('SC', 'AGV', 'RB', 'STA'));
-- 主键已含 DEVICE_TYPE，AGV 与 RB 设备号前缀不同（AGV01 / RB01），不会冲突，无需改主键。


-- ---------- ①-b 如果表已经建好了，只补“设备类型”字段 ----------
-- ALTER TABLE WHLD_WCS.DEVICE_STAT_DAILY ADD (DEVICE_TYPE VARCHAR2(32) DEFAULT 'AGV' NOT NULL);
-- COMMENT ON COLUMN WHLD_WCS.DEVICE_STAT_DAILY.DEVICE_TYPE IS '设备类型：恒AGV';
-- -- 再改主键（老表若只用 STAT_DATE+DEVICE_ID 做键）：
-- ALTER TABLE WHLD_WCS.DEVICE_STAT_DAILY DROP CONSTRAINT PK_DEVICE_STAT_DAILY;
-- ALTER TABLE WHLD_WCS.DEVICE_STAT_DAILY ADD  CONSTRAINT PK_DEVICE_STAT_DAILY PRIMARY KEY (STAT_DATE, DEVICE_TYPE, DEVICE_ID);
-- 主键保留 DEVICE_TYPE：虽然现在只写 AGV，但将来若启用 WCS 的 SC 数据，
-- 两类设备号会撞（AGV01 与 SC01 不同源但都从 1 开始），主键不带类型会互相覆盖。同键 MERGE 也会报 ORA-30926。

-- ---------- ①-c 如果表已经建好了，只补“件数”字段 ----------
-- ALTER TABLE WHLD_WCS.DEVICE_STAT_DAILY ADD (PIECE_QTY NUMBER(18) DEFAULT 0 NOT NULL);
-- COMMENT ON COLUMN WHLD_WCS.DEVICE_STAT_DAILY.PIECE_QTY IS '当日处理件数 SUM(件数字段)';


-- ---------- ② 汇总过程（MERGE 幂等，可重复跑、补跑任意一天；AGV / RB / STA 各一段） ----------
CREATE OR REPLACE PROCEDURE WHLD_WCS.PRC_DEVICE_STAT_DAILY(p_date IN DATE DEFAULT TRUNC(SYSDATE) - 1) IS
BEGIN
  ----------------------------------------------------------------
  -- 第 1 段：AGV 小车（WCS_SUBTASKINFOHISTORY，UPDATETIME 归属）
  ----------------------------------------------------------------
  MERGE INTO WHLD_WCS.DEVICE_STAT_DAILY t
  USING (
    SELECT TRUNC(p_date)  AS stat_date,
           device_type,
           device_id,
           ROUND(SUM(hours), 2) AS sum_hours,
           COUNT(1)             AS sum_qty,
           0                    AS piece_qty    -- 明细表无件数字段，恒 0（见头部说明）
    FROM (
      SELECT 'AGV'                                             AS device_type,
             'AGV' || LPAD(TO_CHAR(AGVCARNO), 2, '0')           AS device_id,
             (FINISHEDTIME - SENDTIME) * 24                     AS hours
      FROM WHLD_WCS.WCS_SUBTASKINFOHISTORY
      WHERE UPDATETIME >= TRUNC(p_date)
        AND UPDATETIME <  TRUNC(p_date) + 1    -- 归属窗口与分组同用 UPDATETIME，别混用 CREATETIME
        AND DEVICETYPE = 'AGV'                 -- 只统计 AGV：SC 堆垛机数据一律取 DT，防双源翻倍
        AND TASKSTATUS = '1'                   -- 只统计已完成；未完成的时间为空会导致口径错位
        AND AGVCARNO  IS NOT NULL
        AND SENDTIME     IS NOT NULL
        AND FINISHEDTIME IS NOT NULL
        AND FINISHEDTIME > SENDTIME            -- 防脏数据产生负数
    )
    WHERE device_id IS NOT NULL
    GROUP BY device_type, device_id      -- 按类型+设备号分组，SC 与 AGV 各自独立统计
  ) s
  ON (    t.stat_date   = s.stat_date
      AND t.device_type = s.device_type
      AND t.device_id   = s.device_id)
  WHEN MATCHED THEN UPDATE
    SET t.sum_hours = s.sum_hours, t.sum_qty = s.sum_qty,
        t.piece_qty = s.piece_qty, t.update_time = SYSDATE
  WHEN NOT MATCHED THEN INSERT
    (stat_date, device_type, device_id, sum_hours, sum_qty, piece_qty, create_time, update_time)
    VALUES (s.stat_date, s.device_type, s.device_id, s.sum_hours, s.sum_qty, s.piece_qty, SYSDATE, SYSDATE);

  ----------------------------------------------------------------
  -- 第 2 段：RB 机械手（WCS_TASKINFO_HISTORY + WMS_M_FINISHEDTASK）
  -- 1 条 WMS 任务(拣选/拆垛)对应多条 WCS 子任务：
  --   内层按 WMS 任务去重：DISASSEMBLY_NUM 用 MAX 只取一次（防按行翻倍），
  --   子任务数 COUNT(wth.TASKID)、最后完成时间 MAX(FINISHDATE)；
  --   外层把 WMS 任务归属到"最后完成日"（只计一次，跨午夜任务不会两天重复计件）。
  --   取双日窗口 [p_date-1, p_date+1) 是为了兜住跨午夜完成的任务，
  --   超过 2 天才收尾的任务极少，其子任务数会略少计，归属日不受影响。
  --   写法与第 1 段完全同构：stat_date/device_type 都从 USING 子查询产出，
  --   ON 和 VALUES 只引用 s. 列，不把 TRUNC(p_date)/'RB' 字面量直接写进 ON。
  ----------------------------------------------------------------
  MERGE INTO WHLD_WCS.DEVICE_STAT_DAILY t
  USING (
    SELECT TRUNC(p_date)                AS stat_date,
           'RB'                         AS device_type,
           r.device_id,
           SUM(r.subtask_cnt)           AS sum_qty,
           SUM(NVL(r.disassembly_num, 0)) AS piece_qty
    FROM (
      SELECT wmf.TASK_NUMBER,
             MAX(CASE WHEN wth.TARGETID IN ('5076','5078','5081') THEN 'RB01'
                      WHEN wth.TARGETID IN ('5085','5087','5090') THEN 'RB02'
                      WHEN wth.TARGETID IN ('5094','5096','5099') THEN 'RB03'
                      WHEN wth.TARGETID IN ('5103','5105','5108') THEN 'RB04'
                      WHEN wth.TARGETID IN ('5112','5114','5117') THEN 'RB05'
                      WHEN wth.TARGETID IN ('5121','5123','5126') THEN 'RB06'
                      WHEN wth.TARGETID IN ('5130','5132','5135') THEN 'RB07'
                      WHEN wth.TARGETID IN ('5139','5141','5144') THEN 'RB08'
                 END)                    AS device_id,
             MAX(wmf.DISASSEMBLY_NUM)     AS disassembly_num,  -- MAX 只为去重取一次，不放大
             COUNT(wth.TASKID)            AS subtask_cnt,
             MAX(wth.FINISHDATE)          AS last_finish
      FROM WHLD_WCS.WCS_TASKINFO_HISTORY wth
      JOIN WHLD_WCS.WMS_M_FINISHEDTASK wmf
        ON  wth.WMSTASKNO      = wmf.TASK_NUMBER
        AND wmf.TASK_TYPE_CODE = 'TaskType_Picking'
      WHERE wth.FINISHDATE >= TRUNC(p_date) - 1
        AND wth.FINISHDATE <  TRUNC(p_date) + 1
        AND wth.TARGETID IN ('5076','5078','5081','5085','5087','5090','5094','5096','5099',
                             '5103','5105','5108','5112','5114','5117','5121','5123','5126',
                             '5130','5132','5135','5139','5141','5144')
      GROUP BY wmf.TASK_NUMBER
    ) r
    WHERE r.device_id   IS NOT NULL
      AND r.last_finish >= TRUNC(p_date)     -- WMS 任务归属到最后完成日，跨天只计一次
      AND r.last_finish <  TRUNC(p_date) + 1
    GROUP BY r.device_id
  ) s
  ON (    t.stat_date   = s.stat_date
      AND t.device_type = s.device_type
      AND t.device_id   = s.device_id)
  WHEN MATCHED THEN UPDATE
    SET t.sum_hours = 0, t.sum_qty = s.sum_qty,
        t.piece_qty = s.piece_qty, t.update_time = SYSDATE
  WHEN NOT MATCHED THEN INSERT
    (stat_date, device_type, device_id, sum_hours, sum_qty, piece_qty, create_time, update_time)
    VALUES (s.stat_date, s.device_type, s.device_id, 0, s.sum_qty, s.piece_qty, SYSDATE, SYSDATE);

  ----------------------------------------------------------------
  -- 第 3 段：STA 站台/输送线（WCS_SUBTASKINFOHISTORY，FINISHEDTIME 归属）
  -- 用户原始 SQL 的问题（已修正）：
  --   a) 'STA' AS device_type, 末尾多了逗号，ORA-00936；
  --   b) WHERE 用 CREATETIME、GROUP BY 用 FINISHEDTIME，过滤列与分组列不一致：
  --      跨天任务漏/重，FINISHEDTIME 为空的行落成 NULL 日期组。统一按 FINISHEDTIME
  --      过滤+归属（与 AGV 段"归属完成日"口径一致，MERGE 重跑可自动修正）。
  --   c) 写入前须先放宽 CHECK 约束放开 'STA'（见 ①-d），否则 ORA-02290。
  -- STA 只统计任务笔数，无时长、无件数口径：SUM_HOURS = 0、PIECE_QTY = 0。
  -- DEVICE_ID 直接取 FROMNODE，若 SP 设备档案里的站台编号是补零格式
  --（如 STA01），把 FROMNODE 换成 'STA' || LPAD(FROMNODE, 2, '0') 即可。
  ----------------------------------------------------------------
  MERGE INTO WHLD_WCS.DEVICE_STAT_DAILY t
  USING (
    SELECT TRUNC(p_date)  AS stat_date,
           device_type,
           device_id,
           0              AS sum_hours,   -- STA 无时长口径，恒 0
           COUNT(1)       AS sum_qty,
           0              AS piece_qty    -- STA 无件数口径，恒 0
    FROM (
      SELECT 'STA'    AS device_type,
             FROMNODE AS device_id
      FROM WHLD_WCS.WCS_SUBTASKINFOHISTORY
      WHERE DEVICETYPE   = 'STA'
        AND FINISHEDTIME >= TRUNC(p_date)  -- 过滤与归属同用 FINISHEDTIME，别混用 CREATETIME
        AND FINISHEDTIME <  TRUNC(p_date) + 1
        AND FROMNODE     IS NOT NULL
    )
    WHERE device_id IS NOT NULL
    GROUP BY device_type, device_id
  ) s
  ON (    t.stat_date   = s.stat_date
      AND t.device_type = s.device_type
      AND t.device_id   = s.device_id)
  WHEN MATCHED THEN UPDATE
    SET t.sum_hours = 0, t.sum_qty = s.sum_qty,
        t.piece_qty = 0, t.update_time = SYSDATE
  WHEN NOT MATCHED THEN INSERT
    (stat_date, device_type, device_id, sum_hours, sum_qty, piece_qty, create_time, update_time)
    VALUES (s.stat_date, s.device_type, s.device_id, 0, s.sum_qty, 0, SYSDATE, SYSDATE);

  COMMIT;
EXCEPTION
  WHEN OTHERS THEN ROLLBACK; RAISE;
END;
/


-- ---------- ③ 每天 01:00 汇总“前一天” ----------
-- 为什么不等到 23:00 统计当天：那样 22:00 之后完成的任务会漏掉且不会自动补回来。
--
-- 用 PLSQL_BLOCK 显式传日期，不用 STORED_PROCEDURE 依赖 p_date 的默认值：
-- Scheduler 解析带 DEFAULT 的参数时容易把参数个数算成 1，
-- 表现为 job 建成功但每次跑都失败在 PLS-00306（参数个数或类型不对）。
--
-- 先删再建，保证脚本可重复执行（重复 CREATE_JOB 会报 ORA-27477 已存在）。
BEGIN
  DBMS_SCHEDULER.DROP_JOB(job_name => 'WHLD_WCS.JOB_DEVICE_STAT_DAILY', force => TRUE);
EXCEPTION WHEN OTHERS THEN NULL;   -- ORA-27475：作业本来就不存在
END;
/

BEGIN
  DBMS_SCHEDULER.CREATE_JOB(
    job_name        => 'WHLD_WCS.JOB_DEVICE_STAT_DAILY',
    job_type        => 'PLSQL_BLOCK',
    job_action      => 'BEGIN WHLD_WCS.PRC_DEVICE_STAT_DAILY(TRUNC(SYSDATE) - 1); END;',
    start_date      => TRUNC(SYSDATE) + 1 + INTERVAL '1' HOUR,   -- 明天 01:00 起
    repeat_interval => 'FREQ=DAILY;BYHOUR=1;BYMINUTE=0;BYSECOND=0',
    enabled         => TRUE,
    comments        => '每天01:00汇总前一天WCS设备任务量与耗时'
  );
END;
/

-- 建完必须验证两件事，否则 job 显示 ENABLED 也可能一次都没跑：
-- 1) 进程数：JOB_QUEUE_PROCESSES = 0 时所有 Scheduler job 都不会执行（对应 MySQL 的 event_scheduler=OFF）
--    SHOW PARAMETER job_queue_processes;    -- 要求 > 0，默认 1000
--    为 0 时让 DBA 执行：ALTER SYSTEM SET job_queue_processes = 1000 SCOPE = BOTH;
-- 2) 立刻试跑一次（不等明天 01:00；前提是①表和②过程都已建好，否则必然失败）。
--    注意：EXEC 是 SQL*Plus 命令，DBeaver 等图形客户端会报 ORA-00900，要用匿名块。
--    use_current_session => FALSE 让它在独立会话里跑、命令立即返回；
--    默认 TRUE 是同步执行，过程跑多久客户端就转多久（明细表大时像卡死）。
--    BEGIN
--      DBMS_SCHEDULER.RUN_JOB(job_name => 'WHLD_WCS.JOB_DEVICE_STAT_DAILY',
--                             use_current_session => FALSE);
--    END;
--    /
--    跑完后查结果（job_name 不带 schema 前缀）：
--    SELECT log_date, status, error#, additional_info
--    FROM user_scheduler_job_run_details
--    WHERE job_name = 'JOB_DEVICE_STAT_DAILY' ORDER BY log_date DESC;
--
--    想看它是不是还在跑（RUN_JOB 后台方式时）：
--    SELECT job_name, elapsed_time, session_id FROM user_scheduler_running_jobs;


-- ---------- ③-b 性能：跑批查询依赖的索引 ----------
-- 【AGV 段】WCS_SUBTASKINFOHISTORY 按 UPDATETIME 过滤。
-- 实查（2026-11-06）：表上现有 3 个索引，全部以 DEVICETYPE, CREATETIME 打头——
--   IDX_SUBTASK_AGV01       (DEVICETYPE, CREATETIME, TASKSTATUS, AGVCARNO, SENDTIME, FINISHEDTIME)
--   IDX_SUBTASK_DEV_CRTTIME (DEVICETYPE, CREATETIME)
--   IDX_SUBTASK_STA_SC      (DEVICETYPE, CREATETIME, TASKSTATUS, DEVICEID, SENDTIME, FINISHEDTIME)
-- 没有任何索引含 UPDATETIME → 按 UPDATETIME 过滤 = 全表扫描，这就是跑批慢的原因。
-- 这三个是 WCS 自己业务的索引，不要动，只新增。
--
-- 【RB 段】WCS_TASKINFO_HISTORY 按 TARGETID IN (24值) + FINISHETIME 双日窗口过滤；
--          WMS_M_FINISHEDTASK 按 TASK_NUMBER 关联取 DISASSEMBLY_NUM。
--
-- 【STA 段】WCS_SUBTASKINFOHISTORY 按 DEVICETYPE='STA' + FINISHEDTIME 过滤。
--          现有索引全部以 DEVICETYPE, CREATETIME 打头，帮不上 FINISHEDTIME；
--          若 STA 数据量大跑批慢，加 (DEVICETYPE, FINISHEDTIME) 复合索引：
--   CREATE INDEX WHLD_WCS.IDX_SUBTASK_DEV_FINTIME
--     ON WHLD_WCS.WCS_SUBTASKINFOHISTORY (DEVICETYPE, FINISHEDTIME) ONLINE;
--
-- 三个索引（建之前先查现有索引，避免重复建；TARGETID 打头可做 24 段窄范围扫描，
-- 比 FINISHDATE 打头（扫全部目标的 2 天）快一个量级）：
--   CREATE INDEX WHLD_WCS.IDX_TASKHIST_TGT_FIN
--     ON WHLD_WCS.WCS_TASKINFO_HISTORY (TARGETID, FINISHDATE) ONLINE;
--   CREATE INDEX WHLD_WCS.IDX_WMFIN_TASKNO
--     ON WHLD_WCS.WMS_M_FINISHEDTASK (TASK_NUMBER) ONLINE;   -- 已有 TASK_NUMBER 索引则跳过
--   CREATE INDEX WHLD_WCS.IDX_SUBTASK_UPDTIME
--     ON WHLD_WCS.WCS_SUBTASKINFOHISTORY (UPDATETIME) ONLINE;
--
-- 建完刷新统计信息（不刷优化器可能仍走全表扫）：
--   BEGIN
--     DBMS_STATS.GATHER_TABLE_STATS('WHLD_WCS','WCS_TASKINFO_HISTORY',   cascade => TRUE);
--     DBMS_STATS.GATHER_TABLE_STATS('WHLD_WCS','WMS_M_FINISHEDTASK',     cascade => TRUE);
--     DBMS_STATS.GATHER_TABLE_STATS('WHLD_WCS','WCS_SUBTASKINFOHISTORY', cascade => TRUE);
--   END;
--   /
--
-- 验证执行计划（应出现 INDEX RANGE SCAN，而不是 TABLE ACCESS FULL）：
--   EXPLAIN PLAN FOR
--   SELECT COUNT(*) FROM WHLD_WCS.WCS_TASKINFO_HISTORY
--   WHERE TARGETID IN ('5076','5078','5081')
--     AND FINISHDATE >= TRUNC(SYSDATE) - 1 AND FINISHDATE < TRUNC(SYSDATE) + 1;
--   SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);
--
-- 代价说明：三张都是高频写入的业务表，每加一个索引插入都略慢、占一份空间，需 WCS/WMS 侧确认。
-- ONLINE 在 11g Enterprise 可用，建索引不阻塞 DML，但仍消耗资源，低峰执行。
-- 如果 WCS 坚决不给加索引，AGV 段退路（方案 B）：改按 CREATETIME 过滤吃现成索引，
-- 但归属日变成"任务创建日"（与现场 UPDATETIME 口径对不上账），且跨天任务漏数，
-- 需 job 每天循环重跑最近 3 天补偿（MERGE 幂等）：
--     job_action => 'BEGIN
--                      WHLD_WCS.PRC_DEVICE_STAT_DAILY(TRUNC(SYSDATE)-1);
--                      WHLD_WCS.PRC_DEVICE_STAT_DAILY(TRUNC(SYSDATE)-2);
--                      WHLD_WCS.PRC_DEVICE_STAT_DAILY(TRUNC(SYSDATE)-3);
--                    END;'
-- 超过 3 天才完成的任务仍会漏（极少）。选 B 需要接受这两点。RB 段没有等价退路，
-- TARGETID+FINISHDATE 索引是硬需求，谈不下来就只能接受跑批慢（01:00 低峰也能接受）。


-- ---------- ④ 历史数据一次性补录（逐天调②过程，AGV 与 RB 一起补） ----------
-- 逐天调过程而不是一条大 SQL：口径与每日跑批完全一致；过程内部是 MERGE，
-- 重复执行只覆盖更新，不会撞主键（ORA-00001）也不会产生重复行。
-- 起始日期 2026-10-29（RB 机械手数据从这天起）；AGV 顺带把 10-29/10-30 也补齐。
-- 注意：逐天调用每天扫一次明细表，日期跨度大时耗时会叠加，属一次性成本。
BEGIN
  FOR d IN (SELECT DATE '2026-10-29' + LEVEL - 1 AS dt
            FROM dual
            CONNECT BY DATE '2026-10-29' + LEVEL - 1 <= TRUNC(SYSDATE) - 1) LOOP
    WHLD_WCS.PRC_DEVICE_STAT_DAILY(d.dt);
  END LOOP;
  COMMIT;
END;
/


-- =============================================================
-- ⑤ 对外交付给 SP（备品备件系统）的只读视图 + 只读账号
-- SP 部署在与 WCS/WMS 同网段的虚拟服务器上，SP 后端用 JDBC 直连本库只读视图拉数据，
-- 不用 DBLINK。列名是 SP 侧固定契约，WMS 库以后按同样的列名提供对象即可零改动接入。
-- =============================================================
CREATE OR REPLACE VIEW WHLD_WCS.VW_SP_DEVICE_STAT_DAILY AS
SELECT STAT_DATE                      AS stat_date,      -- 统计日
       DEVICE_TYPE                    AS device_type,    -- AGV / RB / STA（SC 由 DT 源提供，不在此表）
       DEVICE_ID                      AS device_code,    -- 设备编号
       SUM_HOURS                      AS running_hours,  -- 作业时长(小时)
       SUM_QTY                        AS task_count,     -- 任务数（笔数）
       PIECE_QTY                      AS total_qty       -- 件数（处理件烟/料箱数量）
FROM WHLD_WCS.DEVICE_STAT_DAILY;

COMMENT ON TABLE WHLD_WCS.VW_SP_DEVICE_STAT_DAILY IS
  '对SP系统交付的设备每日运行数据（只读），按 stat_date 增量拉取';

-- 为什么值得多这一层对象（不是性能考虑，视图会被 merge，索引照样用）：
--   1) 列名契约：表里 SUM_QTY/DEVICE_ID → SP 要的 task_count/device_code，改名固定在视图里，
--      SP 侧 query 不易写错；
--   2) 抗变更：将来表加列/启用 SC/加件数字段，只要视图五列不变，SP 侧零改动不重启；
--   3) 授权面小：只读账号只授视图，不授表。
--
-- 如果现场决定不建视图、直接查表，SP 侧 query 改成这样即可（效果完全等价）：
--   select DEVICE_TYPE as device_type, DEVICE_ID as device_code,
--          SUM_HOURS as running_hours, SUM_QTY as task_count, PIECE_QTY as total_qty
--   from WHLD_WCS.DEVICE_STAT_DAILY
--   where STAT_DATE >= ? and STAT_DATE < ?
--   代价：表结构一变就要回来改 SP 配置并重启。

-- 只给 SP 一个只读账号，避免它接触生产明细表。
-- 建用户需要 DBA 权限；建视图用 WHLD_WCS 账号执行。
-- 密码必须自己改：Oracle 12c+ 默认校验复杂度（8位以上、含大小写和数字），否则 ORA-28003。
-- 若库里已有同名用户（ORA-01920），跳过 CREATE USER，改 ALTER USER ... IDENTIFIED BY "...".
-- CREATE USER SP_STAT_READER IDENTIFIED BY "Sp@Stat2026";
-- GRANT CREATE SESSION TO SP_STAT_READER;   -- 12c 起必须显式授，否则连不上（ORA-01045）
-- GRANT SELECT ON WHLD_WCS.VW_SP_DEVICE_STAT_DAILY TO SP_STAT_READER;
-- ALTER USER SP_STAT_READER DEFAULT TABLESPACE USERS QUOTA 0 ON USERS;
--
-- 验证（三步都要过）：
--   SELECT username, account_status, expiry_date FROM dba_users WHERE username = 'SP_STAT_READER';
--   SELECT owner, table_name, privilege FROM dba_tab_privs WHERE grantee = 'SP_STAT_READER';
--   -- 用新账号登录后执行，能出数才算通：
--   SELECT COUNT(*), MIN(stat_date), MAX(stat_date) FROM WHLD_WCS.VW_SP_DEVICE_STAT_DAILY;


-- ---------- 运维常用语句 ----------
-- 手动补跑某一天（EXEC 只在 SQL*Plus 可用，图形客户端用匿名块）
-- BEGIN
--   WHLD_WCS.PRC_DEVICE_STAT_DAILY(DATE '2026-10-26');
-- END;
-- /
--
-- 按类型核对汇总结果
-- SELECT device_type, device_id, sum_hours, sum_qty, piece_qty, update_time
-- FROM WHLD_WCS.DEVICE_STAT_DAILY
-- WHERE stat_date = DATE '2026-10-26'
-- ORDER BY device_type, device_id;
--
-- 按类型汇总（跨天）
-- SELECT device_type, device_id, SUM(sum_hours) AS hours, SUM(sum_qty) AS tasks, SUM(piece_qty) AS pieces
-- FROM WHLD_WCS.DEVICE_STAT_DAILY GROUP BY device_type, device_id ORDER BY 1, 2;
--
-- Job 状态
-- SELECT job_name, enabled, state, last_start_date, next_run_date, failure_count
-- FROM dba_scheduler_jobs WHERE owner = 'WHLD_WCS';
--
-- 跑批日志（失败原因）
-- SELECT log_date, status, error#, additional_info
-- FROM dba_scheduler_job_run_details WHERE job_name = 'JOB_DEVICE_STAT_DAILY' ORDER BY log_id DESC;
--
-- 权限不足 ORA-27486 时，让 DBA 执行：
-- GRANT CREATE JOB TO WHLD_WCS;
