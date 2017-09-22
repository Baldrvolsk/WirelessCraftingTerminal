package p455w0rd.wct.api.recipes.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.oredict.OreDictionary;
import p455w0rd.wct.api.exceptions.MissingIngredientError;
import p455w0rd.wct.api.exceptions.RegistrationError;
import p455w0rd.wct.api.recipes.IIngredient;

public class ShapedRecipe extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe, IRecipeBakeable {
	// Added in for future ease of change, but hard coded for now.
	private static final int MAX_CRAFT_GRID_WIDTH = 3;
	private static final int MAX_CRAFT_GRID_HEIGHT = 3;

	private ItemStack output = ItemStack.EMPTY;
	private Object[] input = null;
	private int width = 0;
	private int height = 0;
	private boolean mirrored = true;
	private boolean disable = false;

	public ShapedRecipe(final ItemStack result, Object... recipe) {
		output = result.copy();

		final StringBuilder shape = new StringBuilder();
		int idx = 0;

		if (recipe[idx] instanceof Boolean) {
			mirrored = (Boolean) recipe[idx];
			if (recipe[idx + 1] instanceof Object[]) {
				recipe = (Object[]) recipe[idx + 1];
			}
			else {
				idx = 1;
			}
		}

		if (recipe[idx] instanceof String[]) {
			final String[] parts = ((String[]) recipe[idx]);
			idx++;

			for (final String s : parts) {
				width = s.length();
				shape.append(s);
			}

			height = parts.length;
		}
		else {
			while (recipe[idx] instanceof String) {
				final String s = (String) recipe[idx];
				idx++;
				shape.append(s);
				width = s.length();
				height++;
			}
		}

		if (width * height != shape.length()) {
			final StringBuilder ret = new StringBuilder("Invalid shaped ore recipe: ");
			for (final Object tmp : recipe) {
				ret.append(tmp).append(", ");
			}
			ret.append(output);
			throw new IllegalStateException(ret.toString());
		}

		final Map<Character, IIngredient> itemMap = new HashMap<>();

		for (; idx < recipe.length; idx += 2) {
			final Character chr = (Character) recipe[idx];
			final Object in = recipe[idx + 1];

			if (in instanceof IIngredient) {
				itemMap.put(chr, (IIngredient) in);
			}
			else {
				final StringBuilder ret = new StringBuilder("Invalid shaped ore recipe: ");
				for (final Object tmp : recipe) {
					ret.append(tmp).append(", ");
				}
				ret.append(output);
				throw new IllegalStateException(ret.toString());
			}
		}

		input = new Object[width * height];
		int x = 0;
		for (final char chr : shape.toString().toCharArray()) {
			input[x] = itemMap.get(chr);
			x++;
		}
	}

	public boolean isEnabled() {
		return !disable;
	}

	@Override
	public boolean matches(final InventoryCrafting inv, final World world) {
		if (disable) {
			return false;
		}

		for (int x = 0; x <= MAX_CRAFT_GRID_WIDTH - width; x++) {
			for (int y = 0; y <= MAX_CRAFT_GRID_HEIGHT - height; ++y) {
				if (checkMatch(inv, x, y, false)) {
					return true;
				}

				if (mirrored && checkMatch(inv, x, y, true)) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public ItemStack getCraftingResult(final InventoryCrafting var1) {
		return output.copy();
	}

	@Override
	public boolean canFit(int i, int i1) {
		return false;
	}

	@Override
	public ItemStack getRecipeOutput() {
		return output;
	}

	@SuppressWarnings("unchecked")
	private boolean checkMatch(final InventoryCrafting inv, final int startX, final int startY, final boolean mirror) {
		if (disable) {
			return false;
		}

		for (int x = 0; x < MAX_CRAFT_GRID_WIDTH; x++) {
			for (int y = 0; y < MAX_CRAFT_GRID_HEIGHT; y++) {
				final int subX = x - startX;
				final int subY = y - startY;
				Object target = null;

				if (subX >= 0 && subY >= 0 && subX < width && subY < height) {
					if (mirror) {
						target = input[width - subX - 1 + subY * width];
					}
					else {
						target = input[subX + subY * width];
					}
				}

				final ItemStack slot = inv.getStackInRowAndColumn(x, y);

				if (target instanceof IIngredient) {
					boolean matched = false;

					try {
						for (final ItemStack item : ((IIngredient) target).getItemStackSet()) {
							matched = matched || checkItemEquals(item, slot);
						}
					}
					catch (final RegistrationError e) {
						// :P
					}
					catch (final MissingIngredientError e) {
						// :P
					}

					if (!matched) {
						return false;
					}
				}
				else if (target instanceof ArrayList) {
					boolean matched = false;

					for (final ItemStack item : (Iterable<ItemStack>) target) {
						matched = matched || checkItemEquals(item, slot);
					}

					if (!matched) {
						return false;
					}
				}
				else if (target == null && !slot.isEmpty()) {
					return false;
				}
			}
		}

		return true;
	}

	private boolean checkItemEquals(final ItemStack target, final ItemStack input) {
		if (input.isEmpty() && !target.isEmpty() || !input.isEmpty() && target.isEmpty()) {
			return false;
		}
		return (target.getItem() == input.getItem() && (target.getItemDamage() == OreDictionary.WILDCARD_VALUE || target.getItemDamage() == input.getItemDamage()));
	}

	public ShapedRecipe setMirrored(final boolean mirror) {
		mirrored = mirror;
		return this;
	}

	/**
	 * Returns the input for this recipe, any mod accessing this value should never manipulate the values in this array
	 * as it will effect the recipe itself.
	 *
	 * @return The recipes input vales.
	 */
	public Object[] getInput() {
		return input;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public Object[] getIIngredients() {
		return input;
	}

	@Override
	public void bake() throws RegistrationError {
		try {
			disable = false;
			for (final Object o : input) {
				if (o instanceof IIngredient) {
					((IIngredient) o).bake();
				}
			}
		}
		catch (final MissingIngredientError err) {
			disable = true;
		}
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(final InventoryCrafting inv) {
		return ForgeHooks.defaultRecipeGetRemainingItems(inv);
	}

}