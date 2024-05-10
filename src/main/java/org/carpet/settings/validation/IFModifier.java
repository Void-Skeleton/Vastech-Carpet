package org.carpet.settings.validation;

import net.minecraft.block.FallingBlock;

public class IFModifier extends Validators.SideEffectValidator<Boolean> {
	@Override
	public Boolean parseValue(Boolean newValue) {
		return false;
	}

	@Override
	public void performEffect(Boolean newValue) {
		FallingBlock.fallImmediately = newValue;
	}
}