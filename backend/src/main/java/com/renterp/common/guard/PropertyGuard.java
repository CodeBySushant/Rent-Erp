package com.renterp.common.guard;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.property.repository.PropertyRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * M8: a closed (deactivated) property takes no new structure, meters, charges,
 * tenants, join requests, access grants or billing runs. Reads, and settling
 * what is still open (payments, move-out), keep working.
 *
 * An unknown id passes here: the caller answers 404 as before.
 */
@Component
public class PropertyGuard {

    private final PropertyRepository properties;

    public PropertyGuard(PropertyRepository properties) {
        this.properties = properties;
    }

    public void requireActive(UUID propertyId) {
        if (propertyId == null) {
            return;
        }
        properties.findById(propertyId).ifPresent(p -> {
            if (!p.isActive()) {
                throw ApiException.conflict("PROPERTY_INACTIVE",
                        "This property has been closed. Changes are no longer possible.");
            }
        });
    }
}
