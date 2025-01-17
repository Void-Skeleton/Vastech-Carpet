package carpet.mixins.rule.ints.pushLimit;

import carpet.settings.CarpetSettings;
//#if MC<=10710
//$$ import net.minecraft.block.PistonBaseBlock;
//#else
import net.minecraft.block.piston.PistonMoveStructureResolver;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

//#if MC<=10710
//$$ @Mixin(value = PistonBaseBlock.class, priority = 420)
//#else
@Mixin(value = PistonMoveStructureResolver.class, priority = 420)
//#endif
public class PistonMoveStructureResolver_pushLimit {
	//#if MC<=10710
	//$$ @ModifyConstant(method = {"canExtend", "push"}, constant = @Constant(intValue = 12))
	//$$ private static int pushLimit(int constant) {
	//#else
	@ModifyConstant(method = "addColumn", constant = @Constant(intValue = 12), expect = 3)
	private int pushLimit(int constant) {
	//#endif
		return CarpetSettings.pushLimit;
	}

	//#if MC<=10710
	//$$ @ModifyConstant(method = {"canExtend", "push"}, constant = @Constant(intValue = 13))
	//$$ private static int pushLimitIterators(int constant) {
	//$$ 	return CarpetSettings.pushLimit + 1;
	//$$ }
	//#endif
}
