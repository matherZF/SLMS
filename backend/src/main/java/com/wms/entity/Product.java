package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "wms_product", uniqueConstraints = {
        @UniqueConstraint(columnNames = "sku")
})
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 255)
    private String spec;

    @Column(length = 32)
    private String unit;

    @Column(length = 64) private String category;
    @Column(length = 64) private String brand;
    @Column(length = 128) private String model;
    @Column(length = 64) private String drawingNo;
    @Column(length = 128) private String manufacturer;
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) not null default 'NORMAL'") private String criticality = "NORMAL";
    @Column(nullable = false, columnDefinition = "bit not null default 0") private boolean vulnerable = false;
    @Column(nullable = false, columnDefinition = "bit not null default 1") private boolean batchManaged = true;
    @Column(nullable = false, columnDefinition = "bit not null default 0") private boolean serialManaged = false;
    private Integer defaultShelfLifeDays;
    /**
     * 已停用：预警只按安全库存算，两个阈值并存时现场不知道该看哪个。
     * 列保留是为了不动历史数据，任何计算都不再读取它。
     */
    @Column(nullable = false, columnDefinition = "int not null default 0") private int minStock = 0;
    private Integer maxStock;
    @Column(nullable = false, columnDefinition = "int not null default 0") private int safetyStock = 0;
    @Column(nullable = false, columnDefinition = "int not null default 0") private int procurementLeadDays = 0;
    @Column(nullable = false, columnDefinition = "bit not null default 1") private boolean enabled = true;
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) not null default 'NONE'") private String lifeMetric = "NONE";
    @Column(precision = 18, scale = 2) private BigDecimal ratedLifeValue;
    /** 多计量口径规则 JSON，第一条即主口径；为空时按 lifeMetric/ratedLifeValue 单口径处理 */
    @Column(columnDefinition = "text") private String lifeMetricRules;
    @Column(precision = 18, scale = 2) private BigDecimal referenceUnitPrice;
    /**
     * @deprecated 已停用：百分比判据对长短寿命的备件提前量相差几十倍（一年寿命提前 73 天、10 天寿命只提前 2 天），
     * 预警改用 {@link #lifeWarningDays} 按剩余天数判断。列保留不删，历史台账快照已经写进去了。
     */
    @Deprecated
    @Column(nullable = false, columnDefinition = "int not null default 80") private int lifeWarningPercent = 80;
    /**
     * 寿命预警提前天数：预计更换日临近这么多天就开始提醒。
     * <b>0 表示跟随系统配置</b>（spare-part.life.warning-days），非 0 才是这个备件单独设的值。
     */
    @Column(nullable = false, columnDefinition = "int not null default 0") private int lifeWarningDays = 0;
    @Column(length = 500) private String remark;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public Product() {}
    public Product(String sku, String name, String spec, String unit) {
        this.sku = sku;
        this.name = name;
        this.spec = spec;
        this.unit = unit;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getCategory() { return category; } public void setCategory(String v) { category = v; }
    public String getBrand() { return brand; } public void setBrand(String v) { brand = v; }
    public String getModel() { return model; } public void setModel(String v) { model = v; }
    public String getDrawingNo() { return drawingNo; } public void setDrawingNo(String v) { drawingNo = v; }
    public String getManufacturer() { return manufacturer; } public void setManufacturer(String v) { manufacturer = v; }
    public String getCriticality() { return criticality; } public void setCriticality(String v) { criticality = v; }
    public boolean isVulnerable() { return vulnerable; } public void setVulnerable(boolean v) { vulnerable = v; }
    public boolean isBatchManaged() { return batchManaged; } public void setBatchManaged(boolean v) { batchManaged = v; }
    public boolean isSerialManaged() { return serialManaged; } public void setSerialManaged(boolean v) { serialManaged = v; }
    public Integer getDefaultShelfLifeDays() { return defaultShelfLifeDays; } public void setDefaultShelfLifeDays(Integer v) { defaultShelfLifeDays = v; }
    public int getMinStock() { return minStock; } public void setMinStock(int v) { minStock = v; }
    public Integer getMaxStock() { return maxStock; } public void setMaxStock(Integer v) { maxStock = v; }
    public int getSafetyStock() { return safetyStock; } public void setSafetyStock(int v) { safetyStock = v; }
    public int getProcurementLeadDays() { return procurementLeadDays; } public void setProcurementLeadDays(int v) { procurementLeadDays = v; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled = v; }
    public String getLifeMetric() { return lifeMetric; } public void setLifeMetric(String v) { lifeMetric = v; }
    public BigDecimal getRatedLifeValue() { return ratedLifeValue; } public void setRatedLifeValue(BigDecimal v) { ratedLifeValue = v; }
    public String getLifeMetricRules() { return lifeMetricRules; } public void setLifeMetricRules(String v) { lifeMetricRules = v; }
    public BigDecimal getReferenceUnitPrice() { return referenceUnitPrice; } public void setReferenceUnitPrice(BigDecimal v) { referenceUnitPrice = v; }
    public int getLifeWarningPercent() { return lifeWarningPercent; } public void setLifeWarningPercent(int v) { lifeWarningPercent = v; }
    public int getLifeWarningDays() { return lifeWarningDays; } public void setLifeWarningDays(int v) { lifeWarningDays = v; }
    public String getRemark() { return remark; } public void setRemark(String v) { remark = v; }
    public Instant getUpdatedAt() { return updatedAt; }
}
