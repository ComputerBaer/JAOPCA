package thelm.jaopca.compat.integrateddynamics.recipes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import thelm.jaopca.api.recipes.IRecipeSerializer;
import thelm.jaopca.utils.MiscHelper;

public class MechanicalSqueezerRecipeSerializer implements IRecipeSerializer {

	private static final Logger LOGGER = LogManager.getLogger();

	public final ResourceLocation key;
	public final Object input;
	public final Object[] itemOutput;
	public final Object fluidOutput;
	public final int fluidOutputAmount;
	public final int time;

	public MechanicalSqueezerRecipeSerializer(ResourceLocation key, Object input, Object[] itemOutput, int time) {
		this(key, input, itemOutput, Fluids.EMPTY, 0, time);
	}

	public MechanicalSqueezerRecipeSerializer(ResourceLocation key, Object input, Object[] itemOutput, Object fluidOutput, int fluidOutputAmount, int time) {
		this.key = Objects.requireNonNull(key);
		this.input = input;
		this.itemOutput = itemOutput;
		this.fluidOutput = fluidOutput;
		this.fluidOutputAmount = fluidOutputAmount;
		this.time = time;
	}

	@Override
	public JsonElement get() {
		Ingredient ing = MiscHelper.INSTANCE.getIngredient(input);
		if(ing.isEmpty()) {
			throw new IllegalArgumentException("Empty ingredient in recipe "+key+": "+input);
		}
		List<Pair<ItemStack, Float>> itemResults = new ArrayList<>();
		int i = 0;
		while(i < itemOutput.length) {
			Object out = itemOutput[i];
			++i;
			Integer count = 1;
			if(i < itemOutput.length && itemOutput[i] instanceof Integer) {
				count = (Integer)itemOutput[i];
				++i;
			}
			Float chance = 1F;
			if(i < itemOutput.length && itemOutput[i] instanceof Float) {
				chance = (Float)itemOutput[i];
				++i;
			}
			ItemStack stack = MiscHelper.INSTANCE.getItemStack(out, count);
			if(stack.isEmpty()) {
				LOGGER.warn("Empty output in recipe {}: {}", key, out);
				continue;
			}
			itemResults.add(Pair.of(stack, chance));
		}
		if(itemResults.isEmpty()) {
			throw new IllegalArgumentException("Empty outputs in recipe "+key+": "+Arrays.deepToString(itemOutput));
		}
		FluidStack fluidStack = MiscHelper.INSTANCE.getFluidStack(fluidOutput, fluidOutputAmount);

		JsonObject json = new JsonObject();
		json.addProperty("type", "integrateddynamics:mechanical_squeezer");
		json.add("item", ing.toJson());
		JsonObject resultJson = new JsonObject();
		JsonArray itemResultJson = new JsonArray();
		for(Pair<ItemStack, Float> pair : itemResults) {
			JsonObject outputJson = new JsonObject();
			JsonObject itemJson = new JsonObject();
			itemJson.addProperty("item", pair.getLeft().getItem().getRegistryName().toString());
			itemJson.addProperty("count", pair.getLeft().getCount());
			outputJson.add("item", itemJson);
			outputJson.addProperty("chance", pair.getRight());
			itemResultJson.add(outputJson);
		}
		resultJson.add("items", itemResultJson);
		if(!fluidStack.isEmpty()) {
			JsonObject fluidResultJson = new JsonObject();
			fluidResultJson.addProperty("fluid", fluidStack.getFluid().getRegistryName().toString());
			fluidResultJson.addProperty("amount", fluidStack.getAmount());
			resultJson.add("fluid", fluidResultJson);
		}
		json.add("result", resultJson);
		json.addProperty("duration", time);

		return json;
	}
}
