package com.wms.entity;

import jakarta.persistence.*;

/**
 * 设备与备件的关联（这台设备上装了几个、多久换一次）。
 *
 * <p>预计寿命与消耗周期已合并为单一的「更换周期」：业务上备件坏了就换，两者本是一回事，
 * 分开填既难保持一致，计算时也只能取其中一个。合并后它同时驱动补货点与补货节奏。</p>
 *
 * <p>这里只存真正属于「这台设备上的这个备件」的两项：装机数量、更换周期。
 * 采购周期、安全库存是备件固有属性（见 {@link Product}），不随设备变化——
 * 同一个 SKU 装在三台设备上只会有一个采购周期、一份共享库存。</p>
 */
@Entity
@Table(name="wms_equipment_part", uniqueConstraints=@UniqueConstraint(columnNames={"equipment_id","product_id"}))
public class EquipmentPart {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="equipment_id", nullable=false) private Equipment equipment;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="product_id", nullable=false) private Product product;
    @Column(nullable=false) private int installedQty = 1;
    /** 更换周期（天）；不填表示不知道多久换一次，补货点只按备件档案的安全库存算 */
    private Integer changeCycleDays;
    public Long getId(){return id;} public Equipment getEquipment(){return equipment;} public void setEquipment(Equipment v){equipment=v;} public Product getProduct(){return product;} public void setProduct(Product v){product=v;}
    public int getInstalledQty(){return installedQty;} public void setInstalledQty(int v){installedQty=v;}
    public Integer getChangeCycleDays(){return changeCycleDays;} public void setChangeCycleDays(Integer v){changeCycleDays=v;}
}
