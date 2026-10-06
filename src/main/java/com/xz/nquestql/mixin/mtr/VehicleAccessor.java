package com.xz.nquestql.mixin.mtr;

import org.mtr.core.data.Vehicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Vehicle.class, remap = false)
public interface VehicleAccessor {

    @Accessor
    long getDoorCooldown();
}
