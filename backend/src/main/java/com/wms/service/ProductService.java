package com.wms.service;

import com.wms.common.BizException;
import com.wms.dto.ProductDTO;
import com.wms.entity.Product;
import com.wms.repository.ProductRepository;
import com.wms.repository.PutawayOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductService {
    private final ProductRepository productRepo;
    private final PutawayOrderRepository putawayRepo;
    private final OperationAuditService auditService;

    public ProductService(ProductRepository productRepo, PutawayOrderRepository putawayRepo, OperationAuditService auditService) {
        this.productRepo = productRepo;
        this.putawayRepo = putawayRepo;
        this.auditService = auditService;
    }

    public Page<ProductDTO> page(String sku, String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        String s = StringUtils.hasText(sku) ? sku : "";
        String n = StringUtils.hasText(name) ? name : "";
        if (!s.isEmpty() || !n.isEmpty()) {
            return productRepo.findBySkuContainingOrNameContaining(s, n, pageable).map(this::toDTO);
        }
        return productRepo.findAll(pageable).map(this::toDTO);
    }

    public ProductDTO getById(Long id) {
        return productRepo.findById(id).map(this::toDTO)
                .orElseThrow(() -> new BizException("商品不存在"));
    }

    @Transactional
    public ProductDTO create(ProductDTO dto) {
        if (!StringUtils.hasText(dto.sku())) throw new BizException("sku不能为空");
        if (!StringUtils.hasText(dto.name())) throw new BizException("name不能为空");
        if (productRepo.existsBySku(dto.sku())) throw new BizException("sku已存在");
        Product p = new Product(dto.sku(), dto.name(), dto.spec(), dto.unit());
        // 新建时默认 0（跟随系统配置）；需要单独设的备件在表单里填具体天数
        applyFields(p, dto);
        productRepo.save(p);
        auditService.record("PRODUCT", p.getId(), p.getSku(), "CREATE", null, null,
                "新增备件：" + p.getName() + "；规格：" + (p.getSpec() == null ? "-" : p.getSpec()));
        return toDTO(p);
    }

    @Transactional
    public ProductDTO update(Long id, ProductDTO dto) {
        Product p = productRepo.findById(id).orElseThrow(() -> new BizException("商品不存在"));
        String before = p.getName() + "/" + p.getSpec() + "/" + p.getUnit() + "/" + p.isEnabled();
        p.setName(dto.name() == null ? p.getName() : dto.name());
        p.setSpec(dto.spec() == null ? p.getSpec() : dto.spec());
        p.setUnit(dto.unit() == null ? p.getUnit() : dto.unit());
        applyFields(p, dto);
        productRepo.save(p);
        auditService.record("PRODUCT", p.getId(), p.getSku(), "UPDATE", null, null,
                "修改前：" + before + "；修改后：" + p.getName() + "/" + p.getSpec() + "/" + p.getUnit() + "/" + p.isEnabled());
        return toDTO(p);
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepo.findById(id).orElseThrow(() -> new BizException("商品不存在"));
        if (putawayRepo.existsByProductId(id)) {
            throw new BizException("该商品已在单据中使用，无法删除");
        }
        productRepo.deleteById(id);
        auditService.record("PRODUCT", id, product.getSku(), "DELETE", null, null, "删除备件：" + product.getName());
    }

    private ProductDTO toDTO(Product p) {
        return new ProductDTO(p.getId(), p.getSku(), p.getName(), p.getSpec(), p.getUnit(),
                p.getCategory(), p.getBrand(), p.getModel(), p.getDrawingNo(), p.getManufacturer(),
                p.getCriticality(), p.isVulnerable(), p.isBatchManaged(), p.isSerialManaged(),
                p.getDefaultShelfLifeDays(), p.getMinStock(), p.getMaxStock(), p.getSafetyStock(),
                p.getProcurementLeadDays(), p.isEnabled(), p.getRemark(), p.getLifeMetric(),
                p.getRatedLifeValue(), p.getLifeWarningPercent(), p.getLifeWarningDays(), p.getReferenceUnitPrice(),
                LifeMetrics.parse(p.getLifeMetricRules(), p.getLifeMetric(), p.getRatedLifeValue()),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private void applyFields(Product p, ProductDTO dto) {
        int min = dto.minStock() == null ? p.getMinStock() : dto.minStock();
        int safety = dto.safetyStock() == null ? p.getSafetyStock() : dto.safetyStock();
        int lead = dto.procurementLeadDays() == null ? p.getProcurementLeadDays() : dto.procurementLeadDays();
        Integer max = dto.maxStock() == null ? p.getMaxStock() : dto.maxStock();
        Integer shelfLife = dto.defaultShelfLifeDays() == null ? p.getDefaultShelfLifeDays() : dto.defaultShelfLifeDays();
        if (min < 0 || safety < 0 || lead < 0 || (max != null && max < 0) || (shelfLife != null && shelfLife < 0)) {
            throw new BizException("库存、采购周期和保质期参数不能为负数");
        }
        if (max != null && max < min) throw new BizException("最高库存不能小于最低库存");
        if (max != null && max < safety) throw new BizException("最高库存不能小于安全库存");
        String criticality = dto.criticality() == null ? p.getCriticality() : dto.criticality().trim().toUpperCase();
        if (!java.util.Set.of("LOW", "NORMAL", "HIGH", "CRITICAL").contains(criticality)) {
            throw new BizException("关键程度参数无效");
        }
        p.setCategory(dto.category()); p.setBrand(dto.brand()); p.setModel(dto.model());
        p.setDrawingNo(dto.drawingNo()); p.setManufacturer(dto.manufacturer()); p.setCriticality(criticality);
        if (dto.vulnerable() != null) p.setVulnerable(dto.vulnerable());
        if (dto.batchManaged() != null) p.setBatchManaged(dto.batchManaged());
        if (dto.serialManaged() != null) p.setSerialManaged(dto.serialManaged());
        p.setDefaultShelfLifeDays(shelfLife); p.setMinStock(min); p.setMaxStock(max);
        p.setSafetyStock(safety); p.setProcurementLeadDays(lead);
        if (dto.enabled() != null) p.setEnabled(dto.enabled());
        String metric = dto.lifeMetric() == null ? p.getLifeMetric() : dto.lifeMetric().trim().toUpperCase();
        if (!java.util.Set.of("NONE", "HOURS", "TASKS", "QTY", "DAYS").contains(metric)) throw new BizException("寿命计量方式无效");
        java.math.BigDecimal ratedLife = dto.ratedLifeValue() == null ? p.getRatedLifeValue() : dto.ratedLifeValue();
        if (!"NONE".equals(metric) && (ratedLife == null || ratedLife.signum() <= 0)) throw new BizException("启用寿命计算时额定寿命必须大于0");
        // 寿命预警改为按天数：预计更换日临近这么多天就提醒。百分比判据已停用（字段保留只为兼容旧数据）
        // 0 = 跟随系统配置里的统一值，非 0 = 这个备件单独设
        int warningDays = dto.lifeWarningDays() == null ? p.getLifeWarningDays() : dto.lifeWarningDays();
        if (warningDays < 0 || warningDays > 180) throw new BizException("寿命预警提前天数必须在0到180之间（0 表示跟随系统配置）");
        // 多计量口径：前端显式传 rules 时以 rules 为准（第一条即主口径）；只传老的单口径字段时自动折算成一条规则
        java.util.List<com.wms.dto.LifeMetricRule> rules;
        String rulesJson;
        if (dto.lifeMetricRules() != null) {
            try {
                rulesJson = LifeMetrics.validateAndSerialize(dto.lifeMetricRules());
            } catch (IllegalArgumentException e) {
                throw new BizException(e.getMessage());
            }
            rules = LifeMetrics.parse(rulesJson, null, null);
        } else {
            rules = "NONE".equals(metric) ? java.util.List.of()
                    : java.util.List.of(new com.wms.dto.LifeMetricRule(metric, ratedLife));
            rulesJson = LifeMetrics.toJson(rules);
        }
        p.setLifeMetricRules(rulesJson);
        p.setLifeMetric(rules.isEmpty() ? "NONE" : rules.get(0).metric());
        p.setRatedLifeValue(rules.isEmpty() ? null : rules.get(0).rated());
        p.setLifeWarningDays(warningDays);
        if(dto.referenceUnitPrice()!=null&&dto.referenceUnitPrice().signum()<0)throw new BizException("参考单价不能为负数");
        p.setReferenceUnitPrice(dto.referenceUnitPrice());
        p.setRemark(dto.remark());
    }
}
