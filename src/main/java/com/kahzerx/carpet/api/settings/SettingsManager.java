package com.kahzerx.carpet.api.settings;

import com.google.common.collect.Sets;
import com.kahzerx.carpet.CarpetSettings;
import com.kahzerx.carpet.utils.CommandHelper;
import com.kahzerx.carpet.utils.Messenger;
import com.kahzerx.carpet.utils.TranslationKeys;
import com.kahzerx.carpet.utils.Translations;
//#if MC>=11300
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
//#else
//$$ import net.minecraft.server.command.exception.CommandSyntaxException;
//$$ import net.minecraft.server.command.AbstractCommand;
//$$ import net.minecraft.server.command.exception.CommandException;
//$$ import net.minecraft.server.command.source.CommandSource;
//$$ import com.google.common.collect.Iterables;
//#endif
//#if MC<=10710
//$$ import net.minecraft.server.command.Command;
//$$ import org.jetbrains.annotations.NotNull;
//#endif
import net.minecraft.server.MinecraftServer;
//#if MC>=11300
import net.minecraft.server.command.handler.CommandManager;
import net.minecraft.server.command.source.CommandSourceStack;
//#else
//$$ import net.minecraft.server.command.source.CommandSource;
//$$ import net.minecraft.util.math.BlockPos;
//$$ import org.jetbrains.annotations.Nullable;
//$$ import java.util.stream.Stream;
//#endif
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static com.kahzerx.carpet.utils.Translations.tr;
import static java.util.Comparator.comparing;

public class SettingsManager {
	private final Map<String, CarpetRule<?>> rules = new HashMap<>();
	private final String version;
	private final String identifier;
	private final String fancyName;
	private boolean locked;
	private MinecraftServer server;
	private final List<RuleObserver> observers = new ArrayList<>();
	private static final List<RuleObserver> staticObservers = new ArrayList<>();

	public SettingsManager(String version, String identifier, String fancyName) {
		this.version = version;
		this.identifier = identifier;
		this.fancyName = fancyName;
	}

	@FunctionalInterface
	public interface RuleObserver {
		//#if MC>=11300
		void ruleChanged(CommandSourceStack source, CarpetRule<?> changedRule, String userInput);
		//#else
		//$$ void ruleChanged(CommandSource source, CarpetRule<?> changedRule, String userInput);
		//#endif
	}

	public void registerRuleObserver(RuleObserver observer) {
		this.observers.add(observer);
	}

	public static void registerGlobalRuleObserver(RuleObserver observer) {
		staticObservers.add(observer);
	}

	public String identifier() {
		return this.identifier;
	}

	public boolean locked() {
		return this.locked;
	}

	public void parseSettingsClass(Class<CarpetSettings> settingsClass) {
		Translations.updateLanguage();
		for (Field f : settingsClass.getDeclaredFields()) {
			Rule rule = f.getAnnotation(Rule.class);
			if (rule == null) {
				continue;
			}
			CarpetRule<?> parsed = new ParsedRule<>(f, rule, this);
			rules.put(parsed.name(), parsed);
		}
	}

	//#if MC>=11300
	public void registerCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
		if (dispatcher.getRoot().getChildren().stream().anyMatch(node -> node.getName().equalsIgnoreCase(this.identifier))) {
			CarpetSettings.LOG.error("Failed to add settings command for " + this.identifier + ". It is masking previous command.");
			return;
		}
		LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = CommandManager.literal(identifier).requires((source) ->
				CommandHelper.canUseCommand(source, CarpetSettings.carpetCommandPermissionLevel) && !locked());

		literalargumentbuilder.
				executes((context)-> listAllSettings(context.getSource())).
				then(CommandManager.literal("list").
						executes( (c) -> listSettings(c.getSource(), String.format(tr(TranslationKeys.ALL_MOD_SETTINGS), fancyName), getRulesSorted())).
						then(CommandManager.argument("tag", StringArgumentType.word()).
								suggests( (c, b)->suggest(getCategories(), b)).
								executes( (c) -> listSettings(c.getSource(), String.format(tr(TranslationKeys.MOD_SETTINGS_MATCHING), fancyName, RuleHelper.translatedCategory(identifier(),StringArgumentType.getString(c, "tag"))), getRulesMatching(StringArgumentType.getString(c, "tag")))))).
//				then(LiteralArgumentBuilder.literal("removeDefault").
//						requires(s -> !locked()).
//						then(RequiredArgumentBuilder.argument("rule", StringArgumentType.word()).
//								suggests( (c, b) -> suggestMatchingContains(getRulesSorted().stream().map(CarpetRule::name), b)).
//								executes((c) -> removeDefault(c.getSource(), contextRule(StringArgumentType.getString(c, "rule")))))).  // TODO
//				then(LiteralArgumentBuilder.literal("setDefault").
//						requires(s -> !locked()).
//						then(RequiredArgumentBuilder.argument("rule", StringArgumentType.word()).
//								suggests( (c, b) -> suggestMatchingContains(getRulesSorted().stream().map(CarpetRule::name), b)).
//								then(RequiredArgumentBuilder.argument("value", StringArgumentType.greedyString()).
//										suggests((c, b)-> suggest(contextRule(StringArgumentType.getString(c, "rule")).suggestions(), b)).
//										executes((c) -> setDefault(c.getSource(), contextRule(StringArgumentType.getString(c, "rule")), StringArgumentType.getString(c, "value")))))).  // TODO
				then(CommandManager.argument("rule", StringArgumentType.word()).
						suggests( (c, b) -> suggestMatchingContains(getRulesSorted().stream().map(CarpetRule::name), b)).
						requires(s -> !locked() ).
						executes( (c) -> displayRuleMenu(c.getSource(), contextRule(StringArgumentType.getString(c, "rule")))).
						then(CommandManager.argument("value", StringArgumentType.greedyString()).
								suggests((c, b)-> suggest(contextRule(StringArgumentType.getString(c, "rule")).suggestions(), b)).
								executes((c) -> setRule(c.getSource(), contextRule(StringArgumentType.getString(c, "rule")), StringArgumentType.getString(c, "value")))));

		dispatcher.register(literalargumentbuilder);
	}

	private CompletableFuture<Suggestions> suggest(Iterable<String> iterable, SuggestionsBuilder suggestionsBuilder) {
		String string = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);
		iterable.forEach(s -> {
			if (this.matchesSubStr(string, s.toLowerCase(Locale.ROOT))) {
				suggestionsBuilder.suggest(s);
			}
		});
		return suggestionsBuilder.buildFuture();
	}
	//#endif

	public void detachServer() {
		for (CarpetRule<?> rule : rules.values()) {
			RuleHelper.resetToDefault(rule, null);
		}
		server = null;
	}

	private boolean matchesSubStr(String string, String string2) {
		for(int i = 0; !string2.startsWith(string, i); ++i) {
			i = string2.indexOf(95, i);
			if (i < 0) {
				return false;
			}
		}
		return true;
	}

	//#if MC>=11300
	private int setRule(CommandSourceStack source, CarpetRule<?> rule, String newValue) {
	//#else
	//$$ private int setRule(CommandSource source, CarpetRule<?> rule, String newValue) {
	//#endif
		try {
			rule.set(source, newValue);
			Messenger.m(source, "w "+ rule +", ", "c ["+ tr(TranslationKeys.CHANGE_PERMANENTLY)+"?]",
					"^w "+String.format(tr(TranslationKeys.CHANGE_PERMANENTLY_HOVER), identifier+".conf"),
					"?/"+identifier+" setDefault "+rule.name()+" "+ RuleHelper.toRuleString(rule.value()));
		} catch (InvalidRuleValueException e) {
			e.notifySource(rule.name(), source);
		}
		return 1;
	}

	//#if MC>=11300
	private CarpetRule<?> contextRule(String ruleName) throws CommandSyntaxException {
	//#else
	//$$ private CarpetRule<?> contextRule(String ruleName) {
	//#endif
		//#if MC>=11300
		CarpetRule<?> rule = getCarpetRule(ruleName);
		if (rule == null) {
			throw new SimpleCommandExceptionType(Messenger.c("rb " + tr(TranslationKeys.UNKNOWN_RULE) + ": " + ruleName)).create();
		}
		return rule;
		//#else
		//$$ return getCarpetRule(ruleName);
		//#endif
	}

	public CarpetRule<?> getCarpetRule(String name) {
		return rules.get(name);
	}

	//#if MC>=11300
	private int displayRuleMenu(CommandSourceStack source, CarpetRule<?> rule) {  // TODO check if there's dupe code around options buttons
	//#else
	//$$ private int displayRuleMenu(CommandSource source, CarpetRule<?> rule) {  // TODO check if there's dupe code around options buttons
	//#endif
		String displayName = RuleHelper.translatedName(rule);

		Messenger.m(source, "");
		Messenger.m(source, "wb "+ displayName ,"!/"+identifier+" "+rule.name(),"^g refresh");
		Messenger.m(source, "w "+ RuleHelper.translatedDescription(rule));

		rule.extraInfo().forEach(s -> Messenger.m(source, s));

		List<Text> tags = new ArrayList<>();
		tags.add(Messenger.c("w "+ tr(TranslationKeys.TAGS)+": "));
		for (String t: rule.categories()) {
			String translated = RuleHelper.translatedCategory(identifier(), t);
			tags.add(Messenger.c("c ["+ translated +"]", "^g "+ String.format(tr(TranslationKeys.LIST_ALL_CATEGORY), translated),"!/"+identifier+" list "+t));
			tags.add(Messenger.c("w , "));
		}
		tags.remove(tags.size() - 1);
		Messenger.m(source, tags.toArray(new Object[0]));

		Messenger.m(source, "w "+ tr(TranslationKeys.CURRENT_VALUE)+": ", String.format("%s %s (%s value)", RuleHelper.getBooleanValue(rule) ? "lb" : "nb", RuleHelper.toRuleString(rule.value()), RuleHelper.isInDefaultValue(rule) ? "default" : "modified"));
		List<Text> options = new ArrayList<>();
		options.add(Messenger.c("w Options: ", "y [ "));
		for (String o: rule.suggestions()) {
			options.add(makeSetRuleButton(rule, o, false));
			options.add(Messenger.c("w  "));
		}
		options.remove(options.size()-1);
		options.add(Messenger.c("y  ]"));
		Messenger.m(source, options.toArray(new Object[0]));

		return 1;
	}

	private Collection<CarpetRule<?>> getRulesMatching(String search) {
		String lcSearch = search.toLowerCase(Locale.ROOT);
		return rules.values().stream().filter(rule -> {
			if (rule.name().toLowerCase(Locale.ROOT).contains(lcSearch)) {
				return true; // substring match, case insensitive
			}
			for (String c : rule.categories()) {
				if (c.equals(search)) {
					return true; // category exactly, case sensitive
				}
			}
			return Sets.newHashSet(RuleHelper.translatedDescription(rule).toLowerCase(Locale.ROOT).split("\\W+")).contains(lcSearch); // contains full term in description, but case insensitive
		}).sorted(comparing(CarpetRule::name)).collect(Collectors.toList());
	}

	//#if MC>=11300
	private CompletableFuture<Suggestions> suggestMatchingContains(Stream<String> stream, SuggestionsBuilder suggestionsBuilder) {
		List<String> regularSuggestionList = new ArrayList<>();
		List<String> smartSuggestionList = new ArrayList<>();
		String query = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);
		stream.forEach((listItem) -> {
			// Regex camelCase Search
			List<String> words = Arrays.stream(listItem.split("(?<!^)(?=[A-Z])")).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
			List<String> prefixes = new ArrayList<>(words.size());
			for (int i = 0; i < words.size(); i++)
				prefixes.add(String.join("", words.subList(i, words.size())));
			if (prefixes.stream().anyMatch(s -> s.startsWith(query))) {
				smartSuggestionList.add(listItem);
			}
			// Regular prefix matching, reference: CommandSource.suggestMatching
			if (this.matchesSubStr(query, listItem.toLowerCase(Locale.ROOT))) {
				regularSuggestionList.add(listItem);
			}
		});
		List<String> filteredSuggestionList = regularSuggestionList.isEmpty() ? smartSuggestionList : regularSuggestionList;
		Objects.requireNonNull(suggestionsBuilder);
		filteredSuggestionList.forEach(suggestionsBuilder::suggest);
		return suggestionsBuilder.buildFuture();
	}
	//#endif

	//#if MC>=11300
	public int listAllSettings(CommandSourceStack source) {
	//#else
	//$$ public int listAllSettings(CommandSource source) {
	//#endif
		int count = listSettings(source, String.format(tr(TranslationKeys.CURRENT_SETTINGS_HEADER), fancyName), getNonDefault());

		if (version != null) {
			Messenger.m(source, "g " + fancyName + " " + tr(TranslationKeys.VERSION) + ": " + version);
		}

		List<String> tags = new ArrayList<>();
		tags.add("w " + tr(TranslationKeys.BROWSE_CATEGORIES)  + ":\n");
		for (String t : this.getCategories()) {
			String translated = RuleHelper.translatedCategory(identifier(), t);
			String translatedPlus = !translated.equals(t) ? String.format("%s (%s)", translated, t) : t;
			tags.add("c [" + translated +"]");
			tags.add("^g " + String.format(tr(TranslationKeys.LIST_ALL_CATEGORY), translatedPlus));
			tags.add("!/"+identifier+" list " + t);
			tags.add("w  ");
		}
		tags.remove(tags.size() - 1);
		Messenger.m(source, tags.toArray(new Object[0]));

		return count;
	}

	public Iterable<String> getCategories() {
		List<String> categories = new ArrayList<>();
		for (CarpetRule<?> rule : this.getCarpetRules()) {
			categories.addAll(rule.categories());
		}
		return new HashSet<>(categories);
	}

	public Collection<CarpetRule<?>> getCarpetRules() {
		return Collections.unmodifiableCollection(rules.values());
	}

	private Collection<CarpetRule<?>> getRulesSorted() {
		return rules.values().stream().sorted(comparing(CarpetRule::name)).collect(Collectors.toList());
	}

	private Collection<CarpetRule<?>> getNonDefault() {
		return rules.values().stream().filter(r -> !RuleHelper.isInDefaultValue(r)).sorted().collect(Collectors.toList());
	}

	//#if MC>=11300
	private int listSettings(CommandSourceStack source, String title, Collection<CarpetRule<?>> settings_list) {
	//#else
	//$$ private int listSettings(CommandSource source, String title, Collection<CarpetRule<?>> settings_list) {
	//#endif
		Messenger.m(source,String.format("wb %s:",title));
		settings_list.forEach(e -> Messenger.m(source, displayInteractiveSetting(e)));
		return settings_list.size();
	}

	private Text displayInteractiveSetting(CarpetRule<?> rule) {
		String displayName = RuleHelper.translatedName(rule);
		List<Object> args = new ArrayList<>();
		args.add("w - "+ displayName +" ");
		args.add("!/"+identifier+" " + rule.name());
		args.add("^y " + RuleHelper.translatedDescription(rule));
		for (String option: rule.suggestions()) {
			args.add(makeSetRuleButton(rule, option, true));
			args.add("w  ");
		}
		if (!rule.suggestions().contains(RuleHelper.toRuleString(rule.value()))) {
			args.add(makeSetRuleButton(rule, RuleHelper.toRuleString(rule.value()), true));
			args.add("w  ");
		}
		args.remove(args.size()-1);
		return Messenger.c(args.toArray(new Object[0]));
	}

	private Text makeSetRuleButton(CarpetRule<?> rule, String option, boolean brackets) {
		String style = RuleHelper.isInDefaultValue(rule)?"g":(option.equalsIgnoreCase(RuleHelper.toRuleString(rule.defaultValue()))?"e":"y");
		if (option.equalsIgnoreCase(RuleHelper.toRuleString(rule.value()))) {
			style = style + "u";
			if (option.equalsIgnoreCase(RuleHelper.toRuleString(rule.defaultValue()))) {
				style = style + "b";
			}
		}
		String component = style + (brackets ? " [" : " ") + option + (brackets ? "]" : "");
		if (option.equalsIgnoreCase(RuleHelper.toRuleString(rule.value()))) {
			return Messenger.c(component);
		}
		return Messenger.c(component, "^g "+ String.format(tr(TranslationKeys.SWITCH_TO), option + (option.equals(RuleHelper.toRuleString(rule.defaultValue())) ? " (default)" : "")), "?/" + identifier + " " + rule.name() + " " + option);
	}

	//#if MC>=11300
	public void notifyRuleChanged(CommandSourceStack source, CarpetRule<?> rule, String userInput) {
	//#else
	//$$ public void notifyRuleChanged(CommandSource source, CarpetRule<?> rule, String userInput) {
	//#endif
		observers.forEach(observer -> observer.ruleChanged(source, rule, userInput));
		staticObservers.forEach(observer -> observer.ruleChanged(source, rule, userInput));
//		ServerNetworkHandler.updateRuleWithConnectedClients(rule);  // TODO
	}

	static class ConfigReadResult {
		private final Map<String, String> ruleMap;
		private final boolean locked;
		public ConfigReadResult(Map<String, String> ruleMap, boolean locked) {
			this.ruleMap = ruleMap;
			this.locked = locked;
		}

		public Map<String, String> getRuleMap() {
			return ruleMap;
		}

		public boolean isLocked() {
			return locked;
		}
	}

	//#if MC<=11202
	//$$ public static class CarpetCommand extends AbstractCommand {
	//$$	private final SettingsManager sm;
	//$$	public CarpetCommand(SettingsManager sm) {
	//$$		this.sm = sm;
	//$$	}
	//$$
	//$$	@Override
	//$$	public String getName() {
	//$$		return this.sm.identifier();
	//$$	}
	//$$
	//$$	@Override
	//$$	public String getUsage(CommandSource commandSource) {
	//$$		return this.sm.identifier() + " <rule> <value>";
	//$$	}
	//$$
	//$$	@Override
		//#if MC>10809
	//$$	public void run(MinecraftServer minecraftServer, CommandSource commandSource, String[] strings) throws CommandException {
		//#else
		//$$ public void run(CommandSource commandSource, String[] strings) throws CommandException {
		//#endif
	//$$		if (strings.length == 0) {
	//$$			this.sm.listAllSettings(commandSource);
	//$$		}
	//$$		if (strings.length == 1) {
	//$$			if ("list".equalsIgnoreCase(strings[0])) {
	//$$				this.sm.listSettings(commandSource, String.format(tr(TranslationKeys.ALL_MOD_SETTINGS), this.sm.fancyName), this.sm.getRulesSorted());
	//$$			} else {
	//$$				CarpetRule<?> rule = this.sm.contextRule(strings[0]);
	//$$				if (rule != null) {
	//$$					this.sm.displayRuleMenu(commandSource, rule);
	//$$				} else {
	//$$					Messenger.c("rb " + tr(TranslationKeys.UNKNOWN_RULE) + ": " + strings[0]);
	//$$				}
	//$$			}
	//$$		}
	//$$		if (strings.length == 2) {
	//$$			if ("list".equalsIgnoreCase(strings[0]) && Iterables.contains(this.sm.getCategories(), strings[1])) {
	//$$				this.sm.listSettings(commandSource, String.format(tr(TranslationKeys.MOD_SETTINGS_MATCHING), this.sm.fancyName, RuleHelper.translatedCategory(this.sm.identifier(), strings[1])), this.sm.getRulesMatching(strings[1]));
	//$$			} else {
	//$$				CarpetRule<?> rule = this.sm.contextRule(strings[0]);
	//$$				if (rule != null) {
	//$$					this.sm.setRule(commandSource, rule, strings[1]);
	//$$				} else {
	//$$					Messenger.c("rb " + tr(TranslationKeys.UNKNOWN_RULE) + ": " + strings[0]);
	//$$				}
	//$$			}
	//$$		}
	//$$	}
	//$$
	//$$	@Override
		//#if MC>10809
	//$$	public boolean canUse(MinecraftServer minecraftServer, CommandSource commandSource) {
		//#else
		//$$ public boolean canUse(CommandSource commandSource) {
		//#endif
	//$$		return CommandHelper.canUseCommand(commandSource, CarpetSettings.carpetCommandPermissionLevel) && !this.sm.locked();
	//$$	}
	//$$
	//$$	@Override
		//#if MC>10809
	//$$	public List<String> getSuggestions(MinecraftServer minecraftServer, CommandSource commandSource, String[] strings, @Nullable BlockPos blockPos) {
		//#elseif MC>10710
		//$$ public List<String> getSuggestions(CommandSource commandSource, String[] strings, @Nullable BlockPos blockPos) {
		//#else
		//$$ public List<String> getSuggestions(CommandSource commandSource, String[] strings) {
		//#endif
	//$$		if (this.sm.locked()) {
	//$$			return Collections.emptyList();
	//$$		}
	//$$		if (strings.length == 1) {
	//$$			List<String> stream = this.sm.getRulesSorted().stream().map(CarpetRule::name).collect(Collectors.toList());
	//$$ 			stream.add("list");
	//$$			List<String> regularSuggestionList = new ArrayList<>();
	//$$			List<String> smartSuggestionList = new ArrayList<>();
	//$$			stream.forEach((listItem) -> {
	//$$				List<String> words = Arrays.stream(listItem.split("(?<!^)(?=[A-Z])")).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
	//$$				List<String> prefixes = new ArrayList<>(words.size());
	//$$				for (int i = 0; i < words.size(); i++)
	//$$					prefixes.add(String.join("", words.subList(i, words.size())));
	//$$				if (prefixes.stream().anyMatch(s -> s.startsWith(strings[0]))) {
	//$$					smartSuggestionList.add(listItem);
	//$$				}
	//$$				if (this.sm.matchesSubStr(strings[0], listItem.toLowerCase(Locale.ROOT))) {
	//$$					regularSuggestionList.add(listItem);
	//$$				}
	//$$			});
	//$$			List<String> filteredSuggestionList = regularSuggestionList.isEmpty() ? smartSuggestionList : regularSuggestionList;
	//$$			return new ArrayList<>(filteredSuggestionList);
	//$$		}
	//$$		if (strings.length == 2) {
	//$$ 			if (strings[0].equalsIgnoreCase("list")) {
	//$$				List<String> categories = new ArrayList<>();
	//$$				this.sm.getCategories().forEach(categories::add);
	//$$				return categories;
	//$$			} else {
	//$$ 				CarpetRule<?> rule = this.sm.contextRule(strings[0]);
	//$$				if (rule != null) {
	//$$					return new ArrayList<>(this.sm.contextRule(strings[0]).suggestions());
	//$$				}
	//$$			}
	//$$		}
	//$$		return Collections.emptyList();
	//$$	}
		//#if MC<=10710
		//$$ @Override
		//$$ public int compareTo(@NotNull Object o) {
		//$$ 	return this.compareTo((Command) o);
		//$$ }
		//#endif
	//$$ }
	//#endif
}
