package com.wms.service;

import com.wms.entity.*;
import com.wms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 首次启动初始化。
 *
 * <p><b>只在从未初始化过的库上执行一次</b>，靠 {@code wms_bootstrap_marker} 表里的标记判断
 * （表由 Flyway V35 创建）。以前这里每次启动都会把缺失的角色 / 数据字典 / 审批流程"补齐"，
 * 导致运维在界面上清理掉的字典项、示例数据在服务重启后又冒出来；现在一次性写完并打标记，
 * 之后的启动只做必要的安全检查，不再往库里写任何初始数据。</p>
 *
 * <p>存量库升级到 V35 时，如果库里已有用户，迁移脚本会直接把标记写上，
 * 所以升级这一次也不会把已清理的数据写回来。</p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    /** 生产部署用：指定首次启动时 admin 的初始密码（至少 12 位） */
    private static final String INIT_ADMIN_PWD_ENV = "SP_INIT_ADMIN_PASSWORD";
    private static final int MIN_ADMIN_PWD_LEN = 12;
    /** 一次性初始化标记：存在即表示这台库已经初始化过，之后不再补写任何初始数据 */
    static final String BOOTSTRAP_MARKER = "DATA_SEED_V1";
    /** 可选：全新库初始化时是否写入演示用的备件/库位/批次，默认不写（生产不需要示例数据） */
    private static final String SEED_DEMO_ENV = "SP_SEED_DEMO_DATA";

    private final ProductRepository productRepo;
    private final LocationRepository locationRepo;
    private final PutawayOrderRepository orderRepo;
    private final InventoryRepository inventoryRepo;
    private final BatchRepository batchRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final DataDictionaryRepository dictionaryRepo;
    private final SparePartUsageRepository sparePartUsageRepo;
    private final JdbcTemplate jdbcTemplate;
    private final RoleRepository roleRepo;
    private final WorkflowDefinitionRepository workflowRepo;
    private final SystemConfigService configService;

    public DataInitializer(ProductRepository productRepo,
                           LocationRepository locationRepo,
                           PutawayOrderRepository orderRepo,
                           InventoryRepository inventoryRepo,
                           BatchRepository batchRepo,
                           UserRepository userRepo,
                           PasswordEncoder passwordEncoder,
                           DataDictionaryRepository dictionaryRepo,
                           SparePartUsageRepository sparePartUsageRepo, JdbcTemplate jdbcTemplate, RoleRepository roleRepo,
                           WorkflowDefinitionRepository workflowRepo, SystemConfigService configService) {
        this.productRepo = productRepo;
        this.locationRepo = locationRepo;
        this.orderRepo = orderRepo;
        this.inventoryRepo = inventoryRepo;
        this.batchRepo = batchRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.dictionaryRepo = dictionaryRepo;
        this.sparePartUsageRepo = sparePartUsageRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.roleRepo = roleRepo;
        this.workflowRepo = workflowRepo;
        this.configService = configService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureUserRoleColumnIsVarchar();
        migrateLegacyOperatorRole();
        ensureAdminAccount();

        if (bootstrapDone()) {
            log.info("[初始化] 该库已初始化过（标记 {}），本次启动不写入任何初始数据", BOOTSTRAP_MARKER);
            return;
        }

        log.info("[初始化] 检测到全新的库，开始写入一次性初始数据（角色 / 系统配置 / 审批流程 / 数据字典）");
        seedRoles();
        seedSystemConfig();
        seedWorkflows();
        backfillOutboundApprovalRecords();
        sparePartUsageRepo.initializeActiveQtyForExistingData();
        seedDictionaries();
        seedDemoDataIfRequested();
        markBootstrapDone();
        log.info("[初始化] 一次性初始数据写入完成，已打标记 {}；后续启动不会再补写", BOOTSTRAP_MARKER);
    }

    /** 标记是否存在。表不存在（如本地 h2 profile 未启用 Flyway）时按"未初始化"处理，仅影响本地开发。 */
    private boolean bootstrapDone() {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "select count(*) from wms_bootstrap_marker where marker = ?", Integer.class, BOOTSTRAP_MARKER);
            return n != null && n > 0;
        } catch (DataAccessException e) {
            log.warn("[初始化] 读取初始化标记失败（本地 h2 环境正常）：{}", e.getMessage());
            return false;
        }
    }

    private void markBootstrapDone() {
        try {
            jdbcTemplate.update("insert ignore into wms_bootstrap_marker(marker) values (?)", BOOTSTRAP_MARKER);
        } catch (DataAccessException e) {
            log.warn("[初始化] 写入初始化标记失败（本地 h2 环境正常）：{}", e.getMessage());
        }
    }

    /** 旧版本 MySQL 曾把角色列建成 ENUM('ADMIN','OPERATOR')，Hibernate update 不会自动扩展 ENUM。 */
    private void ensureUserRoleColumnIsVarchar() {
        // 仅在列仍是 ENUM 时才执行 DDL：避免每次启动都做一次表结构变更（生产账号可能无 ALTER 权限）。
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            if ("MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                String columnType = null;
                try (var probe = connection.createStatement();
                     var rs = probe.executeQuery("""
                             select column_type from information_schema.columns
                              where table_schema = database() and table_name = 'wms_user' and column_name = 'role'""")) {
                    if (rs.next()) columnType = rs.getString(1);
                } catch (Exception e) {
                    log.warn("wms_user.role 列结构探测跳过：{}", e.getMessage());
                }
                if (columnType != null && columnType.toLowerCase().startsWith("enum")) {
                    try (var statement = connection.createStatement()) {
                        statement.execute("alter table wms_user modify column role varchar(16) not null");
                        log.info("已将 wms_user.role 由 {} 改为 varchar(16)", columnType);
                    }
                }
            }
            return null;
        });
    }

    /** 一次性数据迁移：只在存在历史 OPERATOR 角色时执行，避免每次启动都产生写事务 */
    private void migrateLegacyOperatorRole() {
        Integer legacyOperators = jdbcTemplate.queryForObject(
                "select count(*) from wms_user where role='OPERATOR'", Integer.class);
        if (legacyOperators != null && legacyOperators > 0) {
            jdbcTemplate.update("update wms_user set role='WAREHOUSE' where role='OPERATOR'");
            log.info("已将 {} 个历史 OPERATOR 用户迁移为 WAREHOUSE", legacyOperators);
        }
    }

    /**
     * 首次启动创建管理员：不再内置固定弱口令（原 admin/admin123 属于公开可猜凭据）。
     * 优先取环境变量，未配置则生成一次性随机密码并输出到启动日志。
     * 账号已存在时只做弱口令自检告警，不写任何数据。
     */
    private void ensureAdminAccount() {
        if (userRepo.count() == 0) {
            String initPwd = System.getenv(INIT_ADMIN_PWD_ENV);
            if (initPwd != null && !initPwd.isBlank()) {
                initPwd = initPwd.trim();
                if (initPwd.length() < MIN_ADMIN_PWD_LEN) {
                    throw new IllegalStateException(INIT_ADMIN_PWD_ENV + " 长度不足 "
                            + MIN_ADMIN_PWD_LEN + " 位，请设置更强的初始密码");
                }
                log.info("已使用环境变量 {} 指定的密码创建管理员账号 admin", INIT_ADMIN_PWD_ENV);
            } else {
                initPwd = randomPassword();
                log.warn("""

                        ============================================================
                         首次启动已创建管理员账号 admin，初始密码（仅本次输出，请立即记录）：{}

                         该密码由系统随机生成。请登录后立即在【个人信息】中修改。
                         生产部署建议改为通过环境变量 {} 指定初始密码（至少 {} 位），
                         避免依赖启动日志取值。
                        ============================================================
                        """, initPwd, INIT_ADMIN_PWD_ENV, MIN_ADMIN_PWD_LEN);
            }
            User admin = new User("admin", passwordEncoder.encode(initPwd), "管理员", "ADMIN");
            admin.setEnabled(true);
            userRepo.save(admin);
            return;
        }
        // 存量库自检：老版本内置过 admin/admin123 这类公开凭据，
        // 只在日志告警（不阻断启动、不擅自改用户密码）。
        userRepo.findByUsername("admin").ifPresent(u -> {
            for (String weak : java.util.List.of("admin123", "123456", "admin", "password", "12345678")) {
                if (passwordEncoder.matches(weak, u.getPassword())) {
                    log.warn("【安全告警】管理员账号 admin 仍在使用弱口令「{}」，请立即登录修改密码", weak);
                    break;
                }
            }
        });
    }

    private void seedRoles() {
        seedRole("ADMIN","系统管理员","系统全部权限",new java.util.LinkedHashSet<>(PermissionCatalog.ALL.keySet()));
        seedRole("APPROVER","业务审批人","负责业务审批与查询",java.util.Set.of("page:workbench","page:approval","page:inventory","page:usage","page:alerts","outbound:approve"));
        seedRole("WAREHOUSE","仓库业务员","负责领用、入库、出库及库存作业",java.util.Set.of("page:workbench","page:inbound","page:outbound","page:inventory","page:usage","page:stocktake","page:alerts","inbound:create","inbound:putaway","outbound:create","outbound:picking","inventory:transfer","inventory:scrap","stocktake:create","stocktake:complete"));
        seedRole("VIEWER","只读用户","查看数字台账与预警",java.util.Set.of("page:workbench","page:inventory","page:usage","page:alerts"));
        // ADMIN 语义是"全部权限"：采用增量补齐而非整体覆盖。只在首次初始化时执行，
        // 之后管理员在界面上对 ADMIN 的调整不会被启动流程覆盖。
        roleRepo.findByCode("ADMIN").ifPresent(r -> {
            java.util.LinkedHashSet<String> target = new java.util.LinkedHashSet<>(r.getPermissions());
            if (target.addAll(PermissionCatalog.ALL.keySet())) {
                r.setPermissions(target);
                roleRepo.save(r);
                log.info("ADMIN 角色已补齐新增权限码，共 {} 项", target.size());
            }
        });
    }

    private void seedSystemConfig() {
        int added = configService.seed();
        if (added > 0) log.info("已初始化 {} 项系统配置", added);
    }

    private void seedWorkflows() {
        seedWorkflow("OUTBOUND", "领用申请流程", "领用申请审核");
        seedWorkflow("RETURN", "退库审批流程", "退库审批");
        seedWorkflow("SCRAP", "报废审批流程", "报废审批");
        seedWorkflow("STOCKTAKE_DIFF", "盘点差异复核流程", "差异复核");
        workflowRepo.findFirstByBusinessTypeAndEnabledTrueOrderByVersionDesc("OUTBOUND").ifPresent(flow->{
            if("领用审批流程".equals(flow.getName()))flow.setName("领用申请流程");
            flow.getNodes().forEach(node->{if("领用审批".equals(node.getName()))node.setName("领用申请审核");});
            workflowRepo.save(flow);
        });
    }

    /** 历史领用单补齐审批记录（仅首次初始化，之后不再回填被删掉的记录） */
    private void backfillOutboundApprovalRecords() {
        jdbcTemplate.update("""
                insert into wms_approval_record
                    (business_type,business_id,business_no,round_no,applicant,approver,status,opinion,submitted_at,handled_at,created_at)
                select 'OUTBOUND',o.id,o.order_no,1,coalesce(o.applicant,'system'),o.reviewed_by,
                    case when o.status='PENDING_APPROVAL' then 'PENDING'
                         when o.status='REJECTED' then 'REJECTED'
                         when o.status='CANCELLED' then 'CANCELLED' else 'APPROVED' end,
                    o.review_opinion,coalesce(o.created_at,current_timestamp),o.reviewed_at,current_timestamp
                from wms_outbound_order o
                where not exists (select 1 from wms_approval_record a where a.business_type='OUTBOUND' and a.business_id=o.id)
                """);
    }

    private void seedDictionaries() {
        seedDictionary("PRODUCT_CATEGORY", "MECHANICAL", "机械件", 10);
        seedDictionary("PRODUCT_CATEGORY", "ELECTRICAL", "电气件", 20);
        seedDictionary("PRODUCT_CATEGORY", "INSTRUMENT", "仪表件", 30);
        seedDictionary("PRODUCT_CATEGORY", "CONSUMABLE", "耗材", 40);
        seedDictionary("UNIT", "PCS", "个", 10); seedDictionary("UNIT", "PIECE", "件", 20);
        seedDictionary("UNIT", "SET", "套", 30); seedDictionary("UNIT", "BOX", "箱", 40);
        seedDictionary("UNIT", "METER", "米", 50); seedDictionary("UNIT", "KG", "千克", 60);
        seedDictionary("DEPARTMENT", "EQUIPMENT", "设备管理部", 10);
        seedDictionary("DEPARTMENT", "MAINTENANCE", "维修部门", 20);
        seedDictionary("DELIVERY_LOCATION", "WAREHOUSE", "备品备件间", 10);
        seedDictionary("LOCATION_AREA", "RACK", "铁质货架区", 10);
        seedDictionary("LOCATION_AREA", "FLOOR_CONSUMABLE", "地面耗材区", 20);
        seedDictionary("LOCATION_TYPE", "NORMAL", "常温", 10); seedDictionary("LOCATION_TYPE", "CONSUMABLE", "耗材", 20);
        seedDictionary("LOCATION_TYPE", "LARGE", "大件", 30); seedDictionary("LOCATION_TYPE", "HAZARDOUS", "危险品", 40);
        seedDictionary("WAREHOUSE", "SPARE_PARTS_ROOM", "备品备件间", 10);
        seedDictionary("EQUIPMENT_STATUS", "RUNNING", "运行中", 10);
        seedDictionary("EQUIPMENT_STATUS", "STOPPED", "停用", 20);
        seedDictionary("EQUIPMENT_STATUS", "MAINTENANCE", "维护中", 30);

        // 设备区域：item_code 直接存中文名（wms_equipment.zone 存的就是这个值），编码=显示名。
        // 在「系统管理 → 数据字典」里增删改区域后，设备表单的区域下拉自动跟着变。
        seedDictionary("EQUIPMENT_ZONE", "立体库", "立体库", 10);
        seedDictionary("EQUIPMENT_ZONE", "AGV区", "AGV区", 20);
        seedDictionary("EQUIPMENT_ZONE", "机械手区", "机械手区", 30);
        seedDictionary("EQUIPMENT_ZONE", "码垛区", "码垛区", 40);
        seedDictionary("EQUIPMENT_ZONE", "输送线", "输送线", 50);
        seedDictionary("EQUIPMENT_ZONE", "分拣线", "分拣线", 60);
    }

    /**
     * 演示数据：全新库里塞几条备件/库位/批次方便上手。
     * 生产不需要，默认关闭，只有显式设置 SP_SEED_DEMO_DATA=true 才写。
     */
    private void seedDemoDataIfRequested() {
        if (!"true".equalsIgnoreCase(String.valueOf(System.getenv(SEED_DEMO_ENV)))) return;
        if (productRepo.count() != 0) return;

        Product p1 = productRepo.save(new Product("SP-001", "深沟球轴承", "6205-ZZ", "套"));
        Product p2 = productRepo.save(new Product("SP-002", "O型密封圈", "Φ20", "个"));
        Product p3 = productRepo.save(new Product("SP-003", "LED指示灯", "24V", "个"));

        Location l1 = locationRepo.save(new Location("A-01-01", "A区", "常温", null));
        Location l2 = locationRepo.save(new Location("A-01-02", "A区", "常温", null));
        Location l3 = locationRepo.save(new Location("A-02-01", "A区", "大件", null));
        Location l4 = locationRepo.save(new Location("B-01-01", "B区", "常温", null));
        Location l5 = locationRepo.save(new Location("B-01-02", "B区", "冷藏", null));
        Location l6 = locationRepo.save(new Location("C-01-01", "C区", "危险品", null));

        // 种子批次
        Batch b1 = new Batch();
        b1.setItemKey("B-SEED-001");
        b1.setProductId(p1.getId());
        b1.setSku(p1.getSku());
        b1.setProductName(p1.getName());
        b1.setProductionDate(LocalDate.now().minusDays(30));
        b1.setShelfLifeDays(365);
        b1.setManufacturer("瓦轴集团");
        batchRepo.save(b1);

        Batch b2 = new Batch();
        b2.setItemKey("B-SEED-002");
        b2.setProductId(p2.getId());
        b2.setSku(p2.getSku());
        b2.setProductName(p2.getName());
        b2.setProductionDate(LocalDate.now().minusDays(10));
        b2.setShelfLifeDays(730);
        b2.setManufacturer("派克汉尼汾");
        batchRepo.save(b2);

        Batch b3 = new Batch();
        b3.setItemKey("B-SEED-003");
        b3.setProductId(p3.getId());
        b3.setSku(p3.getSku());
        b3.setProductName(p3.getName());
        b3.setProductionDate(LocalDate.now().minusDays(60));
        b3.setShelfLifeDays(180);
        b3.setManufacturer("欧姆龙");
        batchRepo.save(b3);

        PutawayOrder order = new PutawayOrder();
        order.setOrderNo("PASEED0001");
        order.setProduct(p1);
        order.setBatch(b1);
        order.setPlanQty(100);
        order.setPutQty(0);
        order.setStatus(PutawayStatus.PENDING);
        orderRepo.save(order);

        inventoryRepo.save(new Inventory(p2, l1, b2, 50, Instant.now()));
        inventoryRepo.save(new Inventory(p3, l4, b3, 30, Instant.now()));
        log.info("[初始化] 已写入演示用备件/库位/批次（{}={}）", SEED_DEMO_ENV, "true");
    }

    private void seedWorkflow(String type,String name,String nodeName){if(workflowRepo.findFirstByBusinessTypeOrderByVersionDesc(type).isPresent())return;WorkflowDefinition d=new WorkflowDefinition();d.setBusinessType(type);d.setVersion(1);d.setName(name);d.setEnabled(true);WorkflowNode n=new WorkflowNode();n.setDefinition(d);n.setSequence(1);n.setName(nodeName);n.setApproverType("ROLE");n.setApproverValue("APPROVER");d.getNodes().add(n);workflowRepo.save(d);}
    private void seedRole(String code,String name,String desc,java.util.Set<String> permissions){
        if(roleRepo.findByCode(code).isPresent())return; Role r=new Role();r.setCode(code);r.setName(name);r.setDescription(desc);r.setBuiltin(true);r.setEnabled(true);r.setPermissions(new java.util.LinkedHashSet<>(permissions));roleRepo.save(r);
    }

    private void seedDictionary(String type, String code, String label, int sort) {
        if (dictionaryRepo.existsByDictTypeAndItemCode(type, code)) return;
        DataDictionary item = new DataDictionary(); item.setDictType(type); item.setItemCode(code);
        item.setItemLabel(label); item.setSortOrder(sort); item.setEnabled(true); dictionaryRepo.save(item);
    }

    /** 生成 16 位 URL 安全随机密码（约 96 bit 熵），用于首次启动的管理员账号 */
    private static String randomPassword() {
        byte[] buf = new byte[16];
        new java.security.SecureRandom().nextBytes(buf);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf).substring(0, 16);
    }
}
