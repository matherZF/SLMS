package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "wms_equipment", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class Equipment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 128) private String name;
    @Column(length = 128) private String model;
    @Column(length = 128) private String location;
    @Column(length = 64) private String zone;
    @Column(length = 32) private String status = "RUNNING";
    @Column(length = 128) private String manufacturer;
    @Column(length = 255) private String remark;
    @CreationTimestamp @Column(updatable = false) private Instant createdAt;
    public Long getId(){return id;} public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;} public String getModel(){return model;} public void setModel(String v){model=v;}
    public String getLocation(){return location;} public void setLocation(String v){location=v;} public String getZone(){return zone;} public void setZone(String v){zone=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getManufacturer(){return manufacturer;} public void setManufacturer(String v){manufacturer=v;} public String getRemark(){return remark;} public void setRemark(String v){remark=v;} public Instant getCreatedAt(){return createdAt;}
}
