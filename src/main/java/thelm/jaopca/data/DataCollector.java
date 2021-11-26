package thelm.jaopca.data;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.objectweb.asm.Type;

import com.google.common.base.Predicates;
import com.google.common.collect.TreeMultimap;

import net.minecraft.resources.IResourcePack;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.VanillaPack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.packs.ModFileResourcePack;
import net.minecraftforge.forgespi.language.ModFileScanData.AnnotationData;
import thelm.jaopca.api.resources.IPackSupplier;
import thelm.jaopca.api.resources.JAOPCAPackSupplier;

public class DataCollector {

	private DataCollector() {}

	private static final Logger LOGGER = LogManager.getLogger();
	private static final int TAGS_PATH_LENGTH = "tags/".length();
	private static final int RECIPES_PATH_LENGTH = "recipes/".length();
	private static final int LOOT_TABLES_PATH_LENGTH = "loot_tables/".length();
	private static final int ADVANCEMENTS_PATH_LENGTH = "advancements/".length();
	private static final int JSON_EXTENSION_LENGTH = ".json".length();
	private static final Type JAOPCA_PACK_SUPPLIER = Type.getType(JAOPCAPackSupplier.class);
	private static final List<IResourcePack> RESOURCE_PACKS = new ArrayList<>();
	private static final TreeMultimap<String, ResourceLocation> DEFINED_TAGS = TreeMultimap.create();
	private static final TreeSet<ResourceLocation> DEFINED_RECIPES = new TreeSet<>();
	private static final TreeSet<ResourceLocation> DEFINED_LOOT_TABLES = new TreeSet<>();
	private static final TreeSet<ResourceLocation> DEFINED_ADVANCEMENTS = new TreeSet<>();

	public static void collectData() {
		DEFINED_TAGS.clear();
		DEFINED_RECIPES.clear();
		DEFINED_ADVANCEMENTS.clear();
		if(RESOURCE_PACKS.isEmpty()) {
			RESOURCE_PACKS.add(new VanillaPack("minecraft"));
			ModList.get().getModFiles().stream().
			map(mf->new ModFileResourcePack(mf.getFile())).
			forEach(RESOURCE_PACKS::add);
			/*
			 * Fabric:
			 * ModResourcePackUtil.appendModResourcePacks(RESOURCE_PACKS, ResourcePackType.SERVER_DATA);
			 */
			List<AnnotationData> annotationData = ModList.get().getAllScanData().stream().
					flatMap(data->data.getAnnotations().stream()).
					filter(data->JAOPCA_PACK_SUPPLIER.equals(data.getAnnotationType())).
					collect(Collectors.toList());
			for(AnnotationData aData : annotationData) {
				List<String> deps = (List<String>)aData.getAnnotationData().get("modDependencies");
				String className = aData.getClassType().getClassName();
				if(deps != null && deps.stream().filter(Predicates.notNull()).anyMatch(DataCollector::isModVersionNotLoaded)) {
					LOGGER.info("Pack supplier {} has missing mod dependencies, skipping", className);
					continue;
				}
				try {
					Class<?> supplierClass = Class.forName(className);
					Class<? extends IPackSupplier> supplierInstanceClass = supplierClass.asSubclass(IPackSupplier.class);
					IPackSupplier supplier;
					try {
						Method method = supplierClass.getMethod("getInstance");
						supplier = (IPackSupplier)method.invoke(null);
					}
					catch(NoSuchMethodException | InvocationTargetException e) {
						supplier = supplierInstanceClass.newInstance();
					}
					supplier.addPacks(RESOURCE_PACKS::add);
					LOGGER.debug("Loaded pack supplier {}", className);
				}
				catch(ClassNotFoundException | InstantiationException | IllegalAccessException e) {
					LOGGER.fatal("Unable to load pack supplier {}", className, e);
				}
			}
		}
		for(ResourceLocation location : getAllDataResourceLocations("tags", name->name.endsWith(".json"))) {
			String namespace = location.getNamespace();
			String path = location.getPath();
			path = path.substring(TAGS_PATH_LENGTH, path.length()-JSON_EXTENSION_LENGTH);
			String[] split = path.split("/", 2);
			if(split.length == 2) {
				String type = split[0];
				path = split[1];
				DEFINED_TAGS.put(type, new ResourceLocation(namespace, path));
			}
			else {
				LOGGER.error("Tag {} in namespace {} has no type", path, namespace);
			}
		}
		LOGGER.info("Found {} unique defined tags", DEFINED_TAGS.size());
		for(ResourceLocation location : getAllDataResourceLocations("recipes", name->name.endsWith(".json"))) {
			String namespace = location.getNamespace();
			String path = location.getPath();
			if(!path.equals("recipes/_constants.json") && !path.equals("recipes/_factories.json")) {
				path = path.substring(RECIPES_PATH_LENGTH, path.length()-JSON_EXTENSION_LENGTH);
				DEFINED_RECIPES.add(new ResourceLocation(namespace, path));
			}
		}
		LOGGER.info("Found {} unique defined recipes", DEFINED_RECIPES.size());
		for(ResourceLocation location : getAllDataResourceLocations("loot_tables", name->name.endsWith(".json"))) {
			String namespace = location.getNamespace();
			String path = location.getPath();
			path = path.substring(LOOT_TABLES_PATH_LENGTH, path.length()-JSON_EXTENSION_LENGTH);
			DEFINED_LOOT_TABLES.add(new ResourceLocation(namespace, path));
		}
		LOGGER.info("Found {} unique defined loot tables", DEFINED_LOOT_TABLES.size());
		for(ResourceLocation location : getAllDataResourceLocations("advancements", name->name.endsWith(".json"))) {
			String namespace = location.getNamespace();
			String path = location.getPath();
			path = path.substring(ADVANCEMENTS_PATH_LENGTH, path.length()-JSON_EXTENSION_LENGTH);
			DEFINED_ADVANCEMENTS.add(new ResourceLocation(namespace, path));
		}
		LOGGER.info("Found {} unique defined advancements", DEFINED_ADVANCEMENTS.size());
		RESOURCE_PACKS.clear();
	}

	public static Set<ResourceLocation> getDefinedTags(String type) {
		return DEFINED_TAGS.get(type);
	}

	public static Set<ResourceLocation> getDefinedRecipes() {
		return DEFINED_RECIPES;
	}

	public static Set<ResourceLocation> getDefinedLootTables() {
		return DEFINED_LOOT_TABLES;
	}

	public static Set<ResourceLocation> getDefinedAdvancements() {
		return DEFINED_ADVANCEMENTS;
	}

	static boolean isModVersionNotLoaded(String dep) {
		ModList modList = ModList.get();
		int separatorIndex = dep.lastIndexOf('@');
		String modId = dep.substring(0, separatorIndex == -1 ? dep.length() : separatorIndex);
		String spec = separatorIndex == -1 ? "0" : dep.substring(separatorIndex+1, dep.length());
		VersionRange versionRange;
		try {
			versionRange = VersionRange.createFromVersionSpec(spec);
		}
		catch(InvalidVersionSpecificationException e) {
			LOGGER.warn("Unable to parse version spec {} for mod id {}", spec, modId, e);
			return true;
		}
		if(modList.isLoaded(modId)) {
			ArtifactVersion version = modList.getModContainerById(modId).get().getModInfo().getVersion();
			if(versionRange.containsVersion(version)) {
				return false;
			}
			else {
				LOGGER.warn("Mod {} in version range {} was requested, was {}", modId, versionRange, version);
				return true;
			}
		}
		return true;
	}

	static Collection<ResourceLocation> getAllDataResourceLocations(String pathIn, Predicate<String> filter) {
		Set<ResourceLocation> set = new TreeSet<>();
		for(IResourcePack resourcePack : RESOURCE_PACKS) {
			for(String namespace : resourcePack.getResourceNamespaces(ResourcePackType.SERVER_DATA)) {
				set.addAll(resourcePack.getAllResourceLocations(ResourcePackType.SERVER_DATA, namespace, pathIn, Integer.MAX_VALUE, filter));
			}
		}
		return set;
	}
}
