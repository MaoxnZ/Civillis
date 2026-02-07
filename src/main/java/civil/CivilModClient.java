package civil;

import civil.item.CivilDetectorClientParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

/**
 * Client entry point: only responsible for calling each module's client registration (e.g., civilization detector particles etc.).
 */
@Environment(EnvType.CLIENT)
public class CivilModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CivilDetectorClientParticles.register();
    }
}
