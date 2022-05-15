package thelm.jaopca.compat.crossroads.recipes;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import thelm.jaopca.api.recipes.IRecipeSerializer;
import thelm.jaopca.utils.MiscHelper;

public class FluidCoolingRecipeSerializer implements IRecipeSerializer {

	private static final Logger LOGGER = LogManager.getLogger();

	public final ResourceLocation key;
	public final String group;
	public final Object input;
	public final int inputAmount;
	public final Object output;
	public final int outputCount;
	public final float maxTemp;
	public final float addedHeat;

	public FluidCoolingRecipeSerializer(ResourceLocation key, Object input, int inputAmount, Object output, int outputCount, float maxTemp, float addedHeat) {
		this(key, "", input, inputAmount, output, outputCount, maxTemp, addedHeat);
	}

	public FluidCoolingRecipeSerializer(ResourceLocation key, String group, Object input, int inputAmount, Object output, int outputCount, float maxTemp, float addedHeat) {
		this.key = Objects.requireNonNull(key);
		this.group = Strings.nullToEmpty(group);
		this.input = input;
		this.inputAmount = inputAmount;
		this.output = output;
		this.outputCount = outputCount;
		this.maxTemp = maxTemp;
		this.addedHeat = addedHeat;
	}

	@Override
	public JsonElement get() {
		if(input == Fluids.EMPTY || inputAmount <= 0) {
			throw new IllegalArgumentException("Empty ingredient in recipe "+key+": "+input);
		}
		ItemStack stack = MiscHelper.INSTANCE.getItemStack(output, outputCount);
		if(stack.isEmpty()) {
			throw new IllegalArgumentException("Empty output in recipe "+key+": "+output);
		}

		JsonObject json = new JsonObject();
		json.addProperty("type", "crossroads:fluid_cooling");
		if(!group.isEmpty()) {
			json.addProperty("group", group);
		}
		JsonObject ingJson = new JsonObject();
		if(input instanceof String || input instanceof ResourceLocation) {
			ingJson.addProperty("tag", input.toString());
		}
		else if(input instanceof Fluid) {
			ingJson.addProperty("fluid", ((Fluid)input).getRegistryName().toString());
		}
		json.add("input", ingJson);
		json.addProperty("fluid_amount", inputAmount);
		JsonObject resultJson = new JsonObject();
		resultJson.addProperty("item", stack.getItem().getRegistryName().toString());
		resultJson.addProperty("count", stack.getCount());
		json.add("output", resultJson);
		json.addProperty("max_temp", maxTemp);
		json.addProperty("temp_change", addedHeat);

		return json;
	}
}
