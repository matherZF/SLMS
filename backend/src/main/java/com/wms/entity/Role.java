package com.wms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name="wms_role", uniqueConstraints=@UniqueConstraint(columnNames="code"))
public class Role {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=32) private String code;
    @Column(nullable=false,length=64) private String name;
    @Column(length=255) private String description;
    @Column(nullable=false) private boolean builtin;
    @Column(nullable=false) private boolean enabled=true;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="wms_role_permission",joinColumns=@JoinColumn(name="role_id"))
    @Column(name="permission_code",nullable=false,length=64)
    private Set<String> permissions=new LinkedHashSet<>();
    @CreationTimestamp @Column(updatable=false) private Instant createdAt;
    public Long getId(){return id;} public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public boolean isBuiltin(){return builtin;} public void setBuiltin(boolean v){builtin=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public Set<String> getPermissions(){return permissions;} public void setPermissions(Set<String> v){permissions=v;} public Instant getCreatedAt(){return createdAt;}
}
