package com.wms.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "wms_stocktake_item")
public class StocktakeItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "stocktake_order_id", nullable = false)
    private StocktakeOrder order;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;
    @Column(nullable = false)
    private int bookQty;
    private Integer actualQty;

    public Long getId() { return id; }
    public StocktakeOrder getOrder() { return order; }
    public void setOrder(StocktakeOrder order) { this.order = order; }
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public int getBookQty() { return bookQty; }
    public void setBookQty(int bookQty) { this.bookQty = bookQty; }
    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
}
