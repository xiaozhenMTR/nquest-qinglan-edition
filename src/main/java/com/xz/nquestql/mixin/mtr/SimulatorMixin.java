package com.xz.nquestql.mixin.mtr;

import com.xz.nquestql.manager.MtrTracker;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.core.simulation.Simulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(value = Simulator.class, remap = false)
public class SimulatorMixin {

    @Unique
    long nquestql$updateResponseNonce;

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void nquestql$onEndTick(CallbackInfo ci) {
        if (nquestql$updateResponseNonce == MtrTracker.updateRequestNonce) return;
        nquestql$updateResponseNonce = MtrTracker.updateRequestNonce;
        Simulator simulator = (Simulator) (Object) this;

        for (var client : simulator.clients) {
            Position pos = MtrTracker.CLIENT_POSITIONS.get(client.uuid);
            if (pos == null) continue;
            java.util.List<String> stations = new ArrayList<>();
            for (Station station : simulator.stations) {
                if (station.inArea(pos)) stations.add(station.getName());
            }
            MtrTracker.CLIENTS.put(client.uuid, new MtrTracker.ClientState(stations, false, false, -1));
        }

        simulator.sidings.forEach(siding ->
                ((SidingAccessor) (Object) siding).getVehicles().forEach(vehicle ->
                        vehicle.vehicleExtraData.iterateRidingEntities(rider -> {
                            MtrTracker.ClientState st = MtrTracker.CLIENTS.get(rider.uuid);
                            if (st != null) {
                                MtrTracker.CLIENTS.put(rider.uuid, new MtrTracker.ClientState(
                                        st.stations(),
                                        true,
                                        vehicle.vehicleExtraData.getDoorMultiplier() == -1
                                                && ((VehicleAccessor) vehicle).getDoorCooldown() == 0,
                                        ((VehicleSchemaAccessor) vehicle).getSpeed() * 1000 * 3.6
                                ));
                            }
                        })
                )
        );
    }
}
