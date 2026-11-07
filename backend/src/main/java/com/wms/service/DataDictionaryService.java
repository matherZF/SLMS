package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.DataDictionaryDTO;
import com.wms.entity.DataDictionary;
import com.wms.repository.DataDictionaryRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;

@Service
public class DataDictionaryService {
    private final DataDictionaryRepository repository;
    private final OperationAuditService auditService;
    public DataDictionaryService(DataDictionaryRepository repository, OperationAuditService auditService) { this.repository=repository; this.auditService=auditService; }

    public List<DataDictionaryDTO> options(String type) {
        return repository.findByDictTypeAndEnabledTrueOrderBySortOrderAscIdAsc(normalize(type)).stream().map(this::toDTO).toList();
    }
    public Page<DataDictionaryDTO> page(String type, int page, int size) {
        Pageable pageable=PageRequest.of(page,size,Sort.by("dictType").ascending().and(Sort.by("sortOrder")).and(Sort.by("id")));
        return StringUtils.hasText(type) ? repository.findByDictType(normalize(type),pageable).map(this::toDTO) : repository.findAll(pageable).map(this::toDTO);
    }
    @Transactional public DataDictionaryDTO create(DataDictionaryDTO dto) {
        String type=normalize(dto.dictType()), code=required(dto.itemCode(),"字典编码不能为空").toUpperCase();
        if(repository.existsByDictTypeAndItemCode(type,code)) throw new BizException("同类型下字典编码已存在");
        DataDictionary d=new DataDictionary(); d.setDictType(type); d.setItemCode(code); apply(d,dto); repository.save(d);
        auditService.record("DICTIONARY",d.getId(),type+":"+code,"CREATE",null,null,"新增字典项："+d.getItemLabel()); return toDTO(d);
    }
    @Transactional public DataDictionaryDTO update(Long id,DataDictionaryDTO dto) {
        DataDictionary d=repository.findById(id).orElseThrow(()->new BizException("字典项不存在"));
        String before=d.getItemLabel()+"/"+d.isEnabled()+"/"+d.getSortOrder(); apply(d,dto); repository.save(d);
        auditService.record("DICTIONARY",d.getId(),d.getDictType()+":"+d.getItemCode(),"UPDATE",null,null,"修改前："+before+"；修改后："+d.getItemLabel()+"/"+d.isEnabled()+"/"+d.getSortOrder()); return toDTO(d);
    }
    private void apply(DataDictionary d,DataDictionaryDTO dto){d.setItemLabel(required(dto.itemLabel(),"显示名称不能为空"));d.setSortOrder(dto.sortOrder()==null?0:dto.sortOrder());if(dto.enabled()!=null)d.setEnabled(dto.enabled());d.setRemark(StringUtils.hasText(dto.remark())?dto.remark().trim():null);}
    private String normalize(String v){return required(v,"字典类型不能为空").trim().toUpperCase();}
    private String required(String v,String msg){if(!StringUtils.hasText(v))throw new BizException(msg);return v.trim();}
    private DataDictionaryDTO toDTO(DataDictionary d){return new DataDictionaryDTO(d.getId(),d.getDictType(),d.getItemCode(),d.getItemLabel(),d.getSortOrder(),d.isEnabled(),d.getRemark(),d.getCreatedAt(),d.getUpdatedAt());}
}
