package org.alexdev.unlimitednametags.config;

import lombok.AccessLevel;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.MiniPlaceholdersHook;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Different formatting markup options for the TAB list
 */
@SuppressWarnings("unused")
public enum Formatter {

    MINIMESSAGE(
            (plugin, player, text) -> plugin.getHook(MiniPlaceholdersHook.class)
                    .map(hook -> hook.format(text, player))
                    .orElse(getMiniMessage().deserialize(text)),
            "MiniMessage"
    ),
    LEGACY(
            (plugin, player, text) -> getSTUPID().deserialize(replaceHexColorCodes(text)),
            "Legacy Text"
    ),
    UNIVERSAL(
            (plugin, player, text) -> {
                // Step 1: Replace § with & for consistency
                String processed = replaceHexColorCodes(text);
                
                // Step 2: Check if input contains both MiniMessage tags and legacy colors
                boolean hasMiniMessageTags = containsMiniMessageTags(processed);
                boolean hasLegacyColors = containsLegacyColors(processed);
                
                if (hasMiniMessageTags && hasLegacyColors) {
                    // Mixed format: Convert legacy colors to MiniMessage format, then parse all together
                    String converted = convertLegacyColorsToMiniMessage(processed);
                    return MINIMESSAGE.formatter.apply(plugin, player, converted);
                } else if (hasMiniMessageTags) {
                    // Only MiniMessage tags, parse directly
                    return MINIMESSAGE.formatter.apply(plugin, player, processed);
                } else {
                    // Only legacy colors, use legacy conversion path
                    // Step 3: Parse with STUPID serializer (supports &x&0&8&4&c&f&bc format)
                    Component legacyComponent = getSTUPID().deserialize(processed);
                    
                    // Step 4: Serialize to HEX format to normalize all color formats
                    String hexSerialized = getHEX().serialize(legacyComponent);
                    
                    // Step 5: Deserialize back to Component
                    Component hexComponent = getHEX().deserialize(hexSerialized);
                    
                    // Step 6: Convert to MiniMessage format
                    // MiniMessage.serialize() will escape special characters to prevent tag parsing
                    String miniMessageString = getMiniMessage().serialize(hexComponent);
                    
                    // Step 7: Unescape characters for re-parsing
                    // Since we're converting from legacy formats, we need to unescape the characters
                    // that MiniMessage escaped during serialization
                    miniMessageString = unescapeMiniMessageChars(miniMessageString);
                    
                    // Step 8: Apply MiniMessage formatter (with MiniPlaceholders support if available)
                    return MINIMESSAGE.formatter.apply(plugin, player, miniMessageString);
                }
            },
            "Universal"
    );

    // Cached Pattern instances for performance
    @Getter(value = AccessLevel.PRIVATE)
    private static final Pattern MINIMESSAGE_TAG_PATTERN = Pattern.compile(
            "<(?:#([0-9a-fA-F]{3,6})|/?([a-zA-Z][a-zA-Z0-9_]*)(?:[:=].*?)?)>",
            Pattern.CASE_INSENSITIVE
    );
    
    @Getter(value = AccessLevel.PRIVATE)
    private static final Pattern MINIMESSAGE_TAG_PATTERN_FULL = Pattern.compile(
            "<(?:#([0-9a-fA-F]{3,6})|/?([a-zA-Z][a-zA-Z0-9_]*)(?:[:=][^>]*)?)>",
            Pattern.CASE_INSENSITIVE
    );
    
    @Getter(value = AccessLevel.PRIVATE)
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile(
            "&(?:[0-9a-fA-Fk-oK-OrRxX]|#[0-9a-fA-F]{6}|x&[0-9a-fA-F](?:&[0-9a-fA-F]){5})",
            Pattern.CASE_INSENSITIVE
    );
    
    // Cached MiniMessage instance
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    
    @Getter(value = AccessLevel.PRIVATE)
    private static final LegacyComponentSerializer STUPID = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .useUnusualXRepeatedCharacterHexFormat()
            .hexColors()
            .build();
    @Getter(value = AccessLevel.PRIVATE)
    private static final LegacyComponentSerializer HEX = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();

    /**
     * Returns the cached MiniMessage instance.
     * 
     * @return The cached MiniMessage instance
     */
    private static MiniMessage getMiniMessage() {
        return MINI_MESSAGE;
    }

    @NotNull
    private static String replaceHexColorCodes(@NotNull String text) {
        return text.replace('§', '&');
    }

    /**
     * Checks if the text contains MiniMessage tags.
     * MiniMessage tags are enclosed in angle brackets like &lt;tag&gt; or &lt;/tag&gt;.
     * 
     * Common MiniMessage tags include:
     * - Color tags: &lt;color:#hex&gt;, &lt;red&gt;, &lt;blue&gt;, etc.
     * - Gradient tags: &lt;gradient:#color1:#color2&gt;
     * - Formatting tags: &lt;bold&gt;, &lt;italic&gt;, &lt;underlined&gt;, etc.
     * - Reset tags: &lt;reset&gt;, &lt;r&gt;
     * - Hex color tags: &lt;#hexcode&gt; (like &lt;#a8a8a8&gt;)
     * 
     * Pattern matches:
     * - &lt;#[0-9a-fA-F]{3,6}&gt; (hex color tags)
     * - &lt;/?[a-zA-Z][a-zA-Z0-9_]*&gt; (opening/closing tags)
     * - &lt;[a-zA-Z][a-zA-Z0-9_]*[:=] (tags with attributes)
     * 
     * @param text The text to check
     * @return true if the text contains MiniMessage tags, false otherwise
     */
    private static boolean containsMiniMessageTags(@NotNull String text) {
        return MINIMESSAGE_TAG_PATTERN.matcher(text).find();
    }

    /**
     * Checks if the text contains legacy color codes.
     * Legacy color codes include:
     * - Standard colors: &amp;0-&amp;9, &amp;a-&amp;f, &amp;k-&amp;o, &amp;r, &amp;x
     * - Hex colors: &amp;#RRGGBB (e.g., &amp;#fcfcfc)
     * - Legacy hex format: &amp;x&amp;R&amp;R&amp;G&amp;G&amp;B&amp;B
     * 
     * @param text The text to check
     * @return true if the text contains legacy color codes, false otherwise
     */
    private static boolean containsLegacyColors(@NotNull String text) {
        return LEGACY_COLOR_PATTERN.matcher(text).find();
    }

    /**
     * Converts legacy color codes to MiniMessage format while preserving MiniMessage tags.
     * 
     * This method processes the text segment by segment:
     * 1. Identifies segments outside MiniMessage tags that contain legacy colors
     * 2. Parses those segments with legacy serializer
     * 3. Converts the parsed Components to MiniMessage format
     * 4. Replaces the original segments with MiniMessage equivalents
     * 5. Preserves existing MiniMessage tags unchanged
     * 
     * Example:
     * Input: "&lt;bold&gt;&lt;red&gt;hello&lt;/red&gt;&lt;/bold&gt; &amp;cworld &amp;#fcfcfctest"
     * Output: "&lt;bold&gt;&lt;red&gt;hello&lt;/red&gt;&lt;/bold&gt; &lt;red&gt;world&lt;/red&gt; &lt;#fcfcfc&gt;test&lt;/#fcfcfc&gt;"
     * 
     * @param text The text containing both MiniMessage tags and legacy colors
     * @return The text with legacy colors converted to MiniMessage format
     */
    /**
     * Converts a text segment containing legacy colors to MiniMessage format.
     * 
     * @param segment The text segment to convert
     * @param result The StringBuilder to append the result to
     */
    private static void convertLegacySegment(@NotNull String segment, @NotNull StringBuilder result) {
        if (segment.isEmpty()) {
            return;
        }
        
        if (LEGACY_COLOR_PATTERN.matcher(segment).find()) {
            // Segment contains legacy colors, convert it
            Component legacyComponent = getSTUPID().deserialize(segment);
            String miniMessageSegment = getMiniMessage().serialize(legacyComponent);
            miniMessageSegment = unescapeMiniMessageChars(miniMessageSegment);
            result.append(miniMessageSegment);
        } else {
            // No legacy colors, append as-is
            result.append(segment);
        }
    }

    @NotNull
    private static String convertLegacyColorsToMiniMessage(@NotNull String text) {
        // Use a StringBuilder with estimated capacity for efficient string manipulation
        StringBuilder result = new StringBuilder(text.length() + text.length() / 2);
        
        int lastIndex = 0;
        java.util.regex.Matcher tagMatcher = MINIMESSAGE_TAG_PATTERN_FULL.matcher(text);
        
        // Process text segment by segment
        while (tagMatcher.find()) {
            int tagStart = tagMatcher.start();
            int tagEnd = tagMatcher.end();
            
            // Process text before this tag (if any)
            String segmentBeforeTag = text.substring(lastIndex, tagStart);
            convertLegacySegment(segmentBeforeTag, result);
            
            // Append the MiniMessage tag as-is (preserve it)
            result.append(text, tagStart, tagEnd);
            
            lastIndex = tagEnd;
        }
        
        // Process remaining text after last tag
        if (lastIndex < text.length()) {
            String remainingSegment = text.substring(lastIndex);
            convertLegacySegment(remainingSegment, result);
        }
        
        return result.toString();
    }

    /**
     * Unescapes characters that were escaped by MiniMessage serialization.
     * 
     * When MiniMessage.serialize() converts a Component to a string, it escapes
     * special characters to prevent them from being interpreted as MiniMessage tags:
     * - &lt; becomes \&lt; (escaped less-than)
     * - &gt; becomes \&gt; (escaped greater-than)
     * - &amp; becomes \&amp; (escaped ampersand)
     * - \ becomes \\ (escaped backslash)
     * 
     * Since we're converting from legacy formats (which don't use MiniMessage syntax),
     * we need to unescape these characters so they can be properly re-parsed.
     * 
     * Optimized to use a single pass with StringBuilder for better performance.
     * 
     * @param text The text with escaped characters from MiniMessage serialization
     * @return The text with unescaped characters
     */
    @NotNull
    private static String unescapeMiniMessageChars(@NotNull String text) {
        // Early return if no backslashes (no escaped characters)
        if (text.indexOf('\\') == -1) {
            return text;
        }
        
        // Use StringBuilder for efficient single-pass processing
        StringBuilder result = new StringBuilder(text.length());
        int length = text.length();
        
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            
            if (c == '\\' && i + 1 < length) {
                char next = text.charAt(i + 1);
                // Handle escaped characters
                switch (next) {
                    case '\\':
                        result.append('\\');
                        i++; // Skip next character
                        break;
                    case '<':
                        result.append('<');
                        i++; // Skip next character
                        break;
                    case '>':
                        result.append('>');
                        i++; // Skip next character
                        break;
                    case '&':
                        result.append('&');
                        i++; // Skip next character
                        break;
                    default:
                        // Not an escape sequence, keep the backslash
                        result.append(c);
                        break;
                }
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }

    /**
     * Name of the formatter
     */
    @Getter
    private final String name;

    /**
     * Function to apply formatting to a string
     */
    private final TriFunction<UnlimitedNameTags, CommandSender, String, Component> formatter;

    Formatter(@NotNull TriFunction<UnlimitedNameTags, CommandSender, String, Component> formatter, @NotNull String name) {
        this.formatter = formatter;
        this.name = name;
    }

    /**
     * Apply formatting to a string
     *
     * @param text the string to format
     * @return the formatted string
     */
    public Component format(@NotNull UnlimitedNameTags plugin, @NotNull CommandSender audience, @NotNull String text) {
        return formatter.apply(plugin, audience, text);
    }

}
