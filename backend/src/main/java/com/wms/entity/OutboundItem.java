package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "wms_outbound_item")
public class OutboundItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_order_id", nullable = false)
    private OutboundOrder outboundOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * 这批备件装到哪台设备上。放在明细而不是单头：一张领用单可能有多个备件、装在不同设备，
     * 单头一个值会把所有明细记到同一台设备上。
     *
     * <p>为空表示领用人没指定（通用件、或备件没绑定设备），台账建档时再退回基础数据的关联。</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;

    @Column(length = 64) private String equipmentCodeSnapshot;
    @Column(length = 128) private String equipmentNameSnapshot;

    @Column(length = 64) private String productSkuSnapshot;
    @Column(length = 128) private String productNameSnapshot;
    @Column(length = 255) private String productSpecSnapshot;
    @Column(length = 32) private String productUnitSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    /** 拣货来源批次 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(nullable = false)
    private int requestedQty;

    @Column(nullable = false)
    private int pickedQty;

    /** 开始拣货时在库存中占用的数量，完成或取消后归零。 */
    @Column(nullable = false, columnDefinition = "int not null default 0")
    private int reservedQty;

    /** 已经基于该领用明细退回的累计数量。 */
    @Column(nullable = false, columnDefinition = "int not null default 0")
    private int returnedQty;

    @Column(nullable = false)
    private boolean scanVerified;

    private Instant scanVerifiedAt;

    /** 电子标签右下按键已被客户按下 —— completePicking() 的必要条件 */
    @Column(name = "button_pressed", nullable = false, columnDefinition = "bit(1) not null default 0")
    private boolean buttonPressed;

    /** 按键回调到达 WMS 的时间 */
    @Column(name = "button_pressed_at")
    private Instant buttonPressedAt;

    /** 亮灯命令 token（UUID），用于请求-回调匹配 —— startPicking 生成，随 lightTagsLed 请求体 cmdtoken 字段发出 */
    @Column(name = "light_cmdtoken", length = 64)
    private String lightCmdtoken;

    /**
     * 最近一次点亮该库位灯光的时间。
     * <p>
     * 墨水屏服务器的按键回调只带 MAC、不带 cmdtoken，同一个库位上如果有多张拣货单在跑，
     * 无法从报文本身判断是哪一条明细。用"最近一次点灯"作为判定依据：
     * 谁刚刚亮过灯，按下按键就是确认谁。
     */
    @Column(name = "lighted_at")
    private Instant lightedAt;

    /** 亮灯结果确认：null=未收到回调；1=回调 result=true；2=回调 result=false */
    @Column(name = "light_confirmed", columnDefinition = "int null")
    private Integer lightConfirmed;

    /** 亮灯结果回调到达 WMS 的时间 */
    @Column(name = "light_confirmed_at")
    private Instant lightConfirmedAt;

    /** 亮灯结果回调 message 原文（"数据成功" / "标签不存在" / "基站不在线" / "数据发送超时" 等） */
    @Column(name = "light_message", length = 255)
    private String lightMessage;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    public OutboundItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OutboundOrder getOutboundOrder() { return outboundOrder; }
    public void setOutboundOrder(OutboundOrder outboundOrder) { this.outboundOrder = outboundOrder; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Equipment getEquipment() { return equipment; }
    public void setEquipment(Equipment equipment) { this.equipment = equipment; }
    public String getEquipmentCodeSnapshot() { return equipmentCodeSnapshot; } public void setEquipmentCodeSnapshot(String v) { equipmentCodeSnapshot = v; }
    public String getEquipmentNameSnapshot() { return equipmentNameSnapshot; } public void setEquipmentNameSnapshot(String v) { equipmentNameSnapshot = v; }
    public String getProductSkuSnapshot() { return productSkuSnapshot; } public void setProductSkuSnapshot(String v) { productSkuSnapshot = v; }
    public String getProductNameSnapshot() { return productNameSnapshot; } public void setProductNameSnapshot(String v) { productNameSnapshot = v; }
    public String getProductSpecSnapshot() { return productSpecSnapshot; } public void setProductSpecSnapshot(String v) { productSpecSnapshot = v; }
    public String getProductUnitSnapshot() { return productUnitSnapshot; } public void setProductUnitSnapshot(String v) { productUnitSnapshot = v; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public Batch getBatch() { return batch; }
    public void setBatch(Batch batch) { this.batch = batch; }
    public int getRequestedQty() { return requestedQty; }
    public void setRequestedQty(int requestedQty) { this.requestedQty = requestedQty; }
    public int getPickedQty() { return pickedQty; }
    public void setPickedQty(int pickedQty) { this.pickedQty = pickedQty; }
    public int getReservedQty() { return reservedQty; }
    public void setReservedQty(int reservedQty) { this.reservedQty = reservedQty; }
    public int getReturnedQty() { return returnedQty; }
    public void setReturnedQty(int returnedQty) { this.returnedQty = returnedQty; }
    public boolean isScanVerified() { return scanVerified; }
    public void setScanVerified(boolean scanVerified) { this.scanVerified = scanVerified; }
    public Instant getScanVerifiedAt() { return scanVerifiedAt; }
    public void setScanVerifiedAt(Instant scanVerifiedAt) { this.scanVerifiedAt = scanVerifiedAt; }
    public boolean isButtonPressed() { return buttonPressed; }
    public void setButtonPressed(boolean buttonPressed) { this.buttonPressed = buttonPressed; }
    public Instant getButtonPressedAt() { return buttonPressedAt; }
    public void setButtonPressedAt(Instant buttonPressedAt) { this.buttonPressedAt = buttonPressedAt; }
    public String getLightCmdtoken() { return lightCmdtoken; }
    public void setLightCmdtoken(String lightCmdtoken) { this.lightCmdtoken = lightCmdtoken; }
    public Instant getLightedAt() { return lightedAt; }
    public void setLightedAt(Instant lightedAt) { this.lightedAt = lightedAt; }
    public Integer getLightConfirmed() { return lightConfirmed; }
    public void setLightConfirmed(Integer lightConfirmed) { this.lightConfirmed = lightConfirmed; }
    public Instant getLightConfirmedAt() { return lightConfirmedAt; }
    public void setLightConfirmedAt(Instant lightConfirmedAt) { this.lightConfirmedAt = lightConfirmedAt; }
    public String getLightMessage() { return lightMessage; }
    public void setLightMessage(String lightMessage) { this.lightMessage = lightMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
