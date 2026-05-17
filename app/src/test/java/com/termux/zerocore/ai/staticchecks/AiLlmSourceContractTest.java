package com.termux.zerocore.ai.staticchecks;

import com.termux.zerocore.ai.data.AiDatabaseContract;
import com.termux.zerocore.ai.model.ProviderProfileContract;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class AiLlmSourceContractTest {

    @Test
    public void databaseContractUsesSharedCustomChatDbVersionFive() {
        Assert.assertEquals("custom_chat.db", AiDatabaseContract.DATABASE_NAME);
        Assert.assertEquals(5, AiDatabaseContract.DATABASE_VERSION);
    }

    @Test
    public void deepSeekOfficialCurrentModelConstantsArePreserved() {
        Assert.assertEquals("deepseek-v4-flash", ProviderProfileContract.DEEPSEEK_DEFAULT_MODEL);
        Assert.assertEquals("deepseek-v4-pro", ProviderProfileContract.DEEPSEEK_PRO_MODEL);
        Assert.assertEquals("deepseek-chat", ProviderProfileContract.DEEPSEEK_LEGACY_CHAT_MODEL);
        Assert.assertEquals("deepseek-reasoner", ProviderProfileContract.DEEPSEEK_LEGACY_REASONER_MODEL);
    }

    @Test
    public void oldDeepSeekStackReferencesAreRemovedFromActiveSources() throws Exception {
        Path root = sourceRoot();
        try (Stream<Path> stream = Files.walk(root)) {
            String joined = stream
                .filter(path -> Files.isRegularFile(path)
                    && (path.toString().endsWith(".java") || path.toString().endsWith(".kt") || path.toString().endsWith(".xml")))
                .map(this::read)
                .reduce("", (a, b) -> a + "\n" + b);

            Assert.assertFalse(joined.contains("com.termux.zerocore.ai." + "deepseek"));
            Assert.assertFalse(joined.contains("DeepSeek" + "TransitFragment"));
            Assert.assertFalse(joined.contains("ZeroTermux" + "DeepSeekSettingsActivity"));
            Assert.assertFalse(joined.contains("DeepSeek" + "Client"));
            Assert.assertFalse(joined.contains("DATABASE_VERSION" + " = " + "2"));
            Assert.assertFalse(joined.contains("?" + "key="));
        }
    }

    private Path sourceRoot() {
        Path appStyle = Paths.get("src", "main");
        if (Files.exists(appStyle)) return appStyle;
        return Paths.get("app", "src", "main");
    }

    private String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Failed to read " + path, e);
        }
    }
}
