package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "wms_data_dictionary", uniqueConstraints = @UniqueConstraint(name = "uk_dict_type_code", columnNames = {"dict_type", "item_code"}),
        indexes = @Index(name = "idx_dict_type_enabled", columnList = "dict_type,enabled"))
public class DataDictionary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "dict_type", nullable = false, length = 32) private String dictType;
    @Column(name = "item_code", nullable = false, length = 64) private String itemCode;
    @Column(nullable = false, length = 128) private String itemLabel;
    @Column(nullable = false, columnDefinition = "int not null default 0") private int sortOrder;
    @Column(nullable = false, columnDefinition = "bit not null default 1") private boolean enabled = true;
    @Column(length = 255) private String remark;
    @CreationTimestamp @Column(updatable = false) private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    public Long getId(){return id;} public String getDictType(){return dictType;} public void setDictType(String v){dictType=v;}
    public String getItemCode(){return itemCode;} public void setItemCode(String v){itemCode=v;}
    public String getItemLabel(){return itemLabel;} public void setItemLabel(String v){itemLabel=v;}
    public int getSortOrder(){return sortOrder;} public void setSortOrder(int v){sortOrder=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
