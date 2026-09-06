package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.bootstrap.DataExporter;
import io.robrichardson.banklessbank.bootstrap.DwmsImporter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.RuneLite;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import lombok.extern.slf4j.Slf4j;

/**
 * Sidebar panel: shows whether DWMS is present, the last import result, and lets the player kick
 * off a manual import, export all plugin data to a file, or import it back. All importer/exporter
 * calls that touch the client or its config go through the client thread; Swing updates come back
 * via {@link SwingUtilities#invokeLater}.
 */
@Slf4j
public class BanklessBankPanel extends PluginPanel
{
	private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private final BanklessBankPlugin plugin;
	private final DwmsImporter importer;
	private final DataExporter exporter;
	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final SessionManager sessionManager;
	private final BankViewController controller;

	private final JLabel dwmsStatusLabel = new JLabel();
	private final JLabel lastImportLabel = new JLabel();
	private final JButton fillGapsButton = new JButton("Import from DWMS (fill gaps)");
	private final JButton overwriteButton = new JButton("Import from DWMS (overwrite)");
	private final JLabel ignoredPlaceholdersLabel = new JLabel();
	private final JButton clearIgnoredButton = new JButton("Clear ignored placeholders");

	private final JLabel syncLabel = new JLabel();
	private final JButton exportButton = new JButton("Export data…");
	private final JButton importButton = new JButton("Import data…");
	private final JLabel dataStatusLabel = new JLabel();

	@Inject
	public BanklessBankPanel(
		BanklessBankPlugin plugin,
		DwmsImporter importer,
		DataExporter exporter,
		Client client,
		ClientThread clientThread,
		ConfigManager configManager,
		SessionManager sessionManager,
		BankViewController controller)
	{
		this.plugin = plugin;
		this.importer = importer;
		this.exporter = exporter;
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.sessionManager = sessionManager;
		this.controller = controller;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		// DynamicGridLayout, not GridLayout: GridLayout gives every row the height of the tallest
		// one, which the wrapped multi-line hints would blow up to several times a button's height.
		content.setLayout(new DynamicGridLayout(0, 1, 0, 8));

		JLabel title = new JLabel("Bankless Bank");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		content.add(title);

		dwmsStatusLabel.setForeground(Color.LIGHT_GRAY);
		content.add(dwmsStatusLabel);

		lastImportLabel.setForeground(Color.LIGHT_GRAY);
		content.add(lastImportLabel);

		fillGapsButton.addActionListener(e -> runImport(DwmsImporter.Mode.FILL_GAPS));
		fillGapsButton.setBackground(ColorScheme.BRAND_ORANGE);
		fillGapsButton.setHorizontalAlignment(SwingConstants.CENTER);
		content.add(fillGapsButton);

		overwriteButton.addActionListener(e -> runImport(DwmsImporter.Mode.OVERWRITE));
		overwriteButton.setHorizontalAlignment(SwingConstants.CENTER);
		content.add(overwriteButton);

		JLabel hint = new JLabel("<html>Fill gaps only imports storages Bankless Bank has no data for. "
			+ "Overwrite replaces everything with DWMS's data.</html>");
		hint.setForeground(Color.GRAY);
		hint.setFont(hint.getFont().deriveFont(11f));
		content.add(hint);

		ignoredPlaceholdersLabel.setForeground(Color.LIGHT_GRAY);
		content.add(ignoredPlaceholdersLabel);

		clearIgnoredButton.addActionListener(e -> clientThread.invoke(() ->
		{
			if (controller.getViewModel().getLayout().clearPlaceholderIgnores())
			{
				controller.getViewModel().invalidate();
				controller.saveLayoutIfChanged();
			}
			refresh();
		}));
		content.add(clearIgnoredButton);

		content.add(new JSeparator());

		syncLabel.setForeground(Color.LIGHT_GRAY);
		syncLabel.setToolTipText("<html>Bank layout and tracked items always sync with a signed-in RuneLite "
			+ "account. Window position, size and plugin settings additionally need the active RuneLite "
			+ "profile's own sync toggle (Settings &rarr; Profiles).</html>");
		content.add(syncLabel);

		exportButton.addActionListener(e -> runExport());
		exportButton.setHorizontalAlignment(SwingConstants.CENTER);
		content.add(exportButton);

		importButton.addActionListener(e -> runImportData());
		importButton.setHorizontalAlignment(SwingConstants.CENTER);
		content.add(importButton);

		JLabel exportHint = new JLabel("<html>Export saves your bank layout, ignored placeholders, view "
			+ "settings and all tracked storages to a file. Import replaces all of that with the file's "
			+ "contents for the character you're currently logged in as.</html>");
		exportHint.setForeground(Color.GRAY);
		exportHint.setFont(exportHint.getFont().deriveFont(11f));
		content.add(exportHint);

		dataStatusLabel.setForeground(Color.LIGHT_GRAY);
		content.add(dataStatusLabel);

		add(content, BorderLayout.NORTH);

		// Never call setPreferredSize() here: PluginPanel.getPreferredSize() returns the explicitly
		// set height, and RuneLite lays the panel out in a BorderLayout.NORTH inside its scroll pane,
		// so any fixed height (0 included) is taken literally and the whole panel vanishes. The width
		// is already pinned to PANEL_WIDTH by PluginPanel itself.
		refresh();
	}

	private void runImport(DwmsImporter.Mode mode)
	{
		fillGapsButton.setEnabled(false);
		overwriteButton.setEnabled(false);
		lastImportLabel.setText("Importing...");

		// The callback already runs on the client thread, which is where reload() has to happen;
		// refresh() bounces itself to the EDT.
		clientThread.invoke(() -> importer.importNow(mode, result ->
		{
			plugin.reload();
			refresh();
		}));
	}

	// ---- export / import of all plugin data ------------------------------------------------

	private void runExport()
	{
		// 1. flush everything to config on the client thread, then build the JSON there too
		exportButton.setEnabled(false);
		clientThread.invoke(() ->
		{
			// Anything thrown here would otherwise leave the button disabled for good with nothing
			// on screen to say why, since the only path that re-enables it is the file chooser.
			try
			{
				plugin.save();                 // tracked storages
				controller.flush();            // layout, position and view state

				String profileKey = configManager.getRSProfileKey();
				String name = null;
				if (client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
				{
					name = client.getLocalPlayer().getName();
				}
				DataExporter.ExportDocument doc = exporter.buildExport(profileKey, name);
				String json = exporter.toJson(doc);
				String suggested = DataExporter.defaultFileName(doc.displayName, doc.exportedAt);

				SwingUtilities.invokeLater(() -> chooseAndWrite(json, suggested));
			}
			catch (RuntimeException ex)
			{
				log.warn("export failed", ex);
				SwingUtilities.invokeLater(() ->
				{
					exportButton.setEnabled(true);
					JOptionPane.showMessageDialog(this, "Could not read your data: " + ex,
						"Export failed", JOptionPane.ERROR_MESSAGE);
				});
			}
		});
	}

	private void chooseAndWrite(String json, String suggested)
	{
		JFileChooser chooser = new JFileChooser(RuneLite.RUNELITE_DIR);
		chooser.setDialogTitle("Export Bankless Bank data");
		chooser.setSelectedFile(new File(suggested));
		chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			exportButton.setEnabled(true);
			return;
		}

		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".json"))
		{
			file = new File(file.getParentFile(), file.getName() + ".json");
		}
		if (file.exists() && JOptionPane.showConfirmDialog(this,
			file.getName() + " already exists. Overwrite it?", "Overwrite file?",
			JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
		{
			exportButton.setEnabled(true);
			return;
		}

		try
		{
			Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
			dataStatusLabel.setText("<html>Exported to " + file.getName() + "</html>");
		}
		catch (IOException e)
		{
			log.warn("export failed", e);
			JOptionPane.showMessageDialog(this, "Could not write the file: " + e.getMessage(),
				"Export failed", JOptionPane.ERROR_MESSAGE);
		}
		finally
		{
			exportButton.setEnabled(true);
		}
	}

	private void runImportData()
	{
		JFileChooser chooser = new JFileChooser(RuneLite.RUNELITE_DIR);
		chooser.setDialogTitle("Import Bankless Bank data");
		chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}

		final DataExporter.ExportDocument doc;
		try
		{
			String json = new String(Files.readAllBytes(chooser.getSelectedFile().toPath()), StandardCharsets.UTF_8);
			doc = exporter.parse(json);
		}
		catch (IOException | IllegalArgumentException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Import failed", JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (configManager.getRSProfileKey() == null)
		{
			JOptionPane.showMessageDialog(this,
				"Log in to the character you want to import into first — per-character data (your bank "
					+ "layout and tracked storages) is stored per RuneScape account.",
				"Not logged in", JOptionPane.WARNING_MESSAGE);
			return;
		}

		String from = doc.displayName == null || doc.displayName.isEmpty() ? "an unknown character" : doc.displayName;
		String when = TIMESTAMP_FORMAT.format(new Date(doc.exportedAt));
		int confirm = JOptionPane.showConfirmDialog(this,
			"<html>Import " + doc.rsProfileConfig.size() + " storage keys and "
				+ doc.profileConfig.size() + " settings from " + from + ",<br>exported " + when
				+ "?<br><br><b>This replaces all current Bankless Bank data for the character you are "
				+ "logged in as.</b></html>",
			"Import Bankless Bank data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.OK_OPTION)
		{
			return;
		}

		importButton.setEnabled(false);
		clientThread.invoke(() ->
		{
			try
			{
				DataExporter.ImportResult result = exporter.applyImport(doc, configManager.getRSProfileKey());
				plugin.reloadAfterImport();
				SwingUtilities.invokeLater(() ->
				{
					dataStatusLabel.setText("<html>" + result.getMessage() + "</html>");
					importButton.setEnabled(true);
					refresh();
				});
			}
			catch (RuntimeException ex)
			{
				// An import has already cleared both scopes by the time most failures can happen, so
				// the player has to be told rather than left looking at a disabled button.
				log.warn("import failed", ex);
				SwingUtilities.invokeLater(() ->
				{
					dataStatusLabel.setText("<html>Import failed.</html>");
					importButton.setEnabled(true);
					JOptionPane.showMessageDialog(this,
						"The import did not finish: " + ex + "\nYour data may be incomplete; import the "
							+ "file again or restart the client.",
						"Import failed", JOptionPane.ERROR_MESSAGE);
				});
			}
		});
	}

	/** Refreshes the panel from the importer's current status. Safe to call off the client thread. */
	public void refresh()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::refresh);
			return;
		}

		if (!importer.isDwmsInstalled())
		{
			dwmsStatusLabel.setText("<html>DWMS: not installed</html>");
		}
		else if (!importer.isDwmsEnabled())
		{
			dwmsStatusLabel.setText("<html>DWMS: installed, disabled</html>");
		}
		else
		{
			dwmsStatusLabel.setText("<html>DWMS: running</html>");
		}

		DwmsImporter.Result result = importer.getLastResult();
		if (importer.isImporting())
		{
			lastImportLabel.setText("Importing...");
		}
		else if (result == null)
		{
			lastImportLabel.setText("<html>No import run yet.</html>");
		}
		else
		{
			lastImportLabel.setText("<html>" + TIMESTAMP_FORMAT.format(new Date()) + "<br>" + result.getMessage() + "</html>");
		}

		boolean enabled = client.getGameState() == GameState.LOGGED_IN && !importer.isImporting();
		fillGapsButton.setEnabled(enabled);
		overwriteButton.setEnabled(enabled);

		boolean signedIn = sessionManager.getAccountSession() != null;
		syncLabel.setText(signedIn
			? "<html>Cloud sync: on — bank layout and tracked items sync automatically.</html>"
			: "<html>Cloud sync: off — sign in to a RuneLite account, or use Export/Import below.</html>");

		clientThread.invoke(() ->
		{
			int ignored = controller.getViewModel().getLayout().getPlaceholderIgnoreIds().size();
			SwingUtilities.invokeLater(() ->
			{
				ignoredPlaceholdersLabel.setText(ignored == 0
					? "No placeholders ignored."
					: ignored + " placeholder(s) never shown.");
				clearIgnoredButton.setEnabled(ignored > 0);
			});
		});
	}
}
