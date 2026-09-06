package io.robrichardson.banklessbank.bootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import io.robrichardson.banklessbank.BanklessBankConfig;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Behaviour tests for {@link DataExporter} against a mocked {@link ConfigManager}. */
@RunWith(MockitoJUnitRunner.Silent.class)
public class DataExporterTest
{
	private static final String PROFILE = "rsprofile.NEW";

	@Mock private ConfigManager configManager;

	private DataExporter exporter;

	@Before
	public void setUp()
	{
		exporter = new DataExporter(configManager, new Gson());
	}

	@Test
	public void buildExportCapturesBothScopes()
	{
		when(configManager.getConfigurationKeys("banklessbank."))
			.thenReturn(Arrays.asList("banklessbank.viewRows", "banklessbank.placeholders"));
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewRows")).thenReturn("6");
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "placeholders")).thenReturn("true");
		when(configManager.getRSProfileConfigurationKeys(BanklessBankConfig.CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Arrays.asList("layout", "stash.1"));
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn("{}");
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "stash.1")).thenReturn("data");

		DataExporter.ExportDocument doc = exporter.buildExport(PROFILE, "Zezima");

		assertEquals(2, doc.profileConfig.size());
		assertEquals("6", doc.profileConfig.get("viewRows"));
		assertEquals("true", doc.profileConfig.get("placeholders"));
		assertEquals(2, doc.rsProfileConfig.size());
		assertEquals("{}", doc.rsProfileConfig.get("layout"));
		assertEquals("data", doc.rsProfileConfig.get("stash.1"));
		assertEquals(1, doc.schema);
		assertEquals("banklessbank", doc.plugin);
	}

	@Test
	public void buildExportWithNullProfileKeySkipsRsProfileScope()
	{
		when(configManager.getConfigurationKeys("banklessbank.")).thenReturn(Collections.emptyList());

		DataExporter.ExportDocument doc = exporter.buildExport(null, null);

		assertTrue(doc.rsProfileConfig.isEmpty());
		verify(configManager, never()).getRSProfileConfigurationKeys(any(), any(), any());
	}

	@Test
	public void buildExportOmitsKeysWithNullValues()
	{
		when(configManager.getConfigurationKeys("banklessbank."))
			.thenReturn(Collections.singletonList("banklessbank.viewRows"));
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewRows")).thenReturn(null);
		when(configManager.getRSProfileConfigurationKeys(BanklessBankConfig.CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.singletonList("layout"));
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(null);

		DataExporter.ExportDocument doc = exporter.buildExport(PROFILE, null);

		assertTrue(doc.profileConfig.isEmpty());
		assertTrue(doc.rsProfileConfig.isEmpty());
	}

	@Test
	public void roundTripPreservesEveryKeyAndValue()
	{
		when(configManager.getConfigurationKeys("banklessbank."))
			.thenReturn(Collections.singletonList("banklessbank.viewRows"));
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewRows")).thenReturn("6");
		when(configManager.getRSProfileConfigurationKeys(BanklessBankConfig.CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.singletonList("layout"));
		String tricky = "{\"tabs\":[{\"name\":\"a\\\"b\\\\c\"}]}";
		when(configManager.getConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout")).thenReturn(tricky);

		DataExporter.ExportDocument original = exporter.buildExport(PROFILE, "Zezima");
		DataExporter.ExportDocument roundTripped = exporter.parse(exporter.toJson(original));

		assertEquals(original.schema, roundTripped.schema);
		assertEquals(original.plugin, roundTripped.plugin);
		assertEquals(original.exportedAt, roundTripped.exportedAt);
		assertEquals(original.exportedAtIso, roundTripped.exportedAtIso);
		assertEquals(original.rsProfileKey, roundTripped.rsProfileKey);
		assertEquals(original.displayName, roundTripped.displayName);
		assertEquals(original.profileConfig, roundTripped.profileConfig);
		assertEquals(original.rsProfileConfig, roundTripped.rsProfileConfig);
		assertEquals(tricky, roundTripped.rsProfileConfig.get("layout"));
	}

	@Test
	public void parseRejectsForeignPlugin()
	{
		try
		{
			exporter.parse("{\"schema\":1,\"plugin\":\"dudewheresmystuff\"}");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("Bankless Bank"));
		}
	}

	@Test
	public void parseRejectsFutureSchema()
	{
		try
		{
			exporter.parse("{\"schema\":99,\"plugin\":\"banklessbank\"}");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("99"));
		}
	}

	@Test
	public void parseRejectsMalformedJson()
	{
		try
		{
			exporter.parse("{not json");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// good: not a raw JsonSyntaxException
		}
	}

	@Test
	public void parseDefaultsMissingMapsToEmpty()
	{
		DataExporter.ExportDocument doc = exporter.parse("{\"schema\":1,\"plugin\":\"banklessbank\"}");

		assertNotNull(doc.profileConfig);
		assertNotNull(doc.rsProfileConfig);
		assertTrue(doc.profileConfig.isEmpty());
		assertTrue(doc.rsProfileConfig.isEmpty());
	}

	@Test
	public void parseRejectsAJsonObjectWithNoPluginField()
	{
		// An import replaces everything, so an unrelated .json must never validate: if the document's
		// own identifying fields default to ours, "{}" reads as an empty Bankless Bank export and the
		// import wipes the layout and every tracked storage without writing anything back.
		try
		{
			exporter.parse("{}");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("Bankless Bank"));
		}
	}

	@Test
	public void parseRejectsAnUnrelatedJsonDocument()
	{
		try
		{
			exporter.parse("{\"some\":\"other\",\"tool\":[1,2,3]}");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("Bankless Bank"));
		}
	}

	@Test
	public void parseRejectsOurPluginNameWithNoSchemaVersion()
	{
		try
		{
			exporter.parse("{\"plugin\":\"banklessbank\"}");
			fail("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("version"));
		}
	}

	@Test
	public void applyImportUnsetsExistingKeysBeforeWriting()
	{
		when(configManager.getConfigurationKeys("banklessbank."))
			.thenReturn(Collections.singletonList("banklessbank.viewRows"));
		when(configManager.getRSProfileConfigurationKeys(BanklessBankConfig.CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.singletonList("layout"));

		DataExporter.ExportDocument doc = new DataExporter.ExportDocument();
		doc.profileConfig.put("viewRows", "8");
		doc.rsProfileConfig.put("layout", "{}");

		exporter.applyImport(doc, PROFILE);

		InOrder inOrder = Mockito.inOrder(configManager);
		inOrder.verify(configManager).unsetConfiguration(BanklessBankConfig.CONFIG_GROUP, null, "viewRows");
		inOrder.verify(configManager).unsetConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout");
		inOrder.verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "viewRows", "8");
		inOrder.verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, PROFILE, "layout", "{}");
	}

	@Test
	public void applyImportWritesToTheCurrentProfileNotTheRecordedOne()
	{
		when(configManager.getConfigurationKeys("banklessbank.")).thenReturn(Collections.emptyList());
		when(configManager.getRSProfileConfigurationKeys(eq(BanklessBankConfig.CONFIG_GROUP), any(), eq("")))
			.thenReturn(Collections.emptyList());

		DataExporter.ExportDocument doc = new DataExporter.ExportDocument();
		doc.rsProfileKey = "rsprofile.OLD";
		doc.rsProfileConfig.put("layout", "{}");

		exporter.applyImport(doc, "rsprofile.NEW");

		verify(configManager).setConfiguration(BanklessBankConfig.CONFIG_GROUP, "rsprofile.NEW", "layout", "{}");
		verify(configManager, never()).setConfiguration(eq(BanklessBankConfig.CONFIG_GROUP), eq("rsprofile.OLD"), any(), any());
	}

	@Test
	public void applyImportSkipsUnwritableKeys()
	{
		when(configManager.getConfigurationKeys("banklessbank.")).thenReturn(Collections.emptyList());
		when(configManager.getRSProfileConfigurationKeys(BanklessBankConfig.CONFIG_GROUP, PROFILE, ""))
			.thenReturn(Collections.emptyList());

		DataExporter.ExportDocument doc = new DataExporter.ExportDocument();
		doc.rsProfileConfig.put("bad:key", "x");
		doc.rsProfileConfig.put("$weird", "y");

		DataExporter.ImportResult result = exporter.applyImport(doc, PROFILE);

		verify(configManager, never()).setConfiguration(eq(BanklessBankConfig.CONFIG_GROUP), eq(PROFILE), eq("bad:key"), any());
		verify(configManager, never()).setConfiguration(eq(BanklessBankConfig.CONFIG_GROUP), eq(PROFILE), eq("$weird"), any());
		assertEquals(2, result.getKeysSkipped());
	}

	@Test
	public void applyImportWithNullTargetProfileWritesSettingsOnly()
	{
		when(configManager.getConfigurationKeys("banklessbank.")).thenReturn(Collections.emptyList());

		DataExporter.ExportDocument doc = new DataExporter.ExportDocument();
		doc.profileConfig.put("viewRows", "8");
		doc.profileConfig.put("placeholders", "true");
		doc.rsProfileConfig.put("layout", "{}");

		DataExporter.ImportResult result = exporter.applyImport(doc, null);

		assertEquals(0, result.getRsProfileKeysWritten());
		assertEquals(2, result.getProfileKeysWritten());
		assertTrue(result.getMessage(), result.getMessage().contains("Not logged in"));
		verify(configManager, never()).getRSProfileConfigurationKeys(any(), any(), any());
		verify(configManager, never()).setConfiguration(eq(BanklessBankConfig.CONFIG_GROUP), any(), eq("layout"), any());
	}

	@Test
	public void defaultFileNameSanitisesDisplayName()
	{
		long when = 0L;
		String fileName = DataExporter.defaultFileName("Zez ima!", when);

		assertTrue(fileName, fileName.startsWith("banklessbank-Zez_ima_-"));
		assertTrue(fileName.endsWith(".json"));

		String withoutName = DataExporter.defaultFileName(null, when);
		assertTrue(withoutName, withoutName.startsWith("banklessbank-export-"));
	}

	@Test
	public void isWritableKeyRejectsColonAndDollarPrefix()
	{
		assertFalse(DataExporter.isWritableKey(null));
		assertFalse(DataExporter.isWritableKey(""));
		assertFalse(DataExporter.isWritableKey("bad:key"));
		assertFalse(DataExporter.isWritableKey("$weird"));
		assertTrue(DataExporter.isWritableKey("layout"));
	}
}
