package com.wms;

import com.wms.common.BizException;
import com.wms.service.PermissionCatalog;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCatalogTests {

    @Test
    void everyButtonPermissionHasARegisteredPageParent() {
        PermissionCatalog.ALL.keySet().stream()
                .filter(code -> !code.startsWith("page:"))
                .forEach(code -> assertTrue(PermissionCatalog.PARENTS.containsKey(code), code));
    }

    @Test
    void buttonPermissionRequiresItsPagePermission() {
        assertThrows(BizException.class,
                () -> PermissionCatalog.validate(Set.of("outbound:manage")));
        assertDoesNotThrow(() -> PermissionCatalog.validate(
                Set.of("page:outbound", "outbound:manage")));
    }

    @Test
    void rejectsUnknownPermissionCodes() {
        assertThrows(BizException.class,
                () -> PermissionCatalog.validate(Set.of("system:unknown")));
    }
}
