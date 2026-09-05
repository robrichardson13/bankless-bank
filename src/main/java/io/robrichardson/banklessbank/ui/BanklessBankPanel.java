package io.robrichardson.banklessbank.ui;

import io.robrichardson.banklessbank.BanklessBankPlugin;
import io.robrichardson.banklessbank.bootstrap.DwmsImporter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel: shows whether DWMS is present, the last import result, and lets the player kick
 * off a manual import. All importer calls are dispatched to the client thread; Swing updates come
 * back via {@link SwingUtilities#invokeLater}.
 */
public class BanklessBankPanel extends PluginPanel
{
	private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private final BanklessBankPlugin plugin;
	private final DwmsImporter importer;
	private final net.runelite.api.Client client;
	private final ClientThread clientThread;

	private final JLabel dwmsStatusLabel = new JLabel();
	private final JLabel lastImportLabel = new JLabel();
	private final JButton fillGapsButton = new JButton("Import from DWMS (fill gaps)");
	private final JButton overwriteButton = new JButton("Import from DWMS (overwrite)");

	@Inject
	public BanklessBankPanel(
		BanklessBankPlugin plugin,
		DwmsImporter importer,
		net.runelite.api.Client client,
		ClientThread clientThread)
	{
		this.plugin = plugin;
		this.importer = importer;
		this.client = client;
		this.clientThread = clientThread;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new GridLayout(0, 1, 0, 8));

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
		content.add(fillGapsButton);

		overwriteButton.addActionListener(e -> runImport(DwmsImporter.Mode.OVERWRITE));
		content.add(overwriteButton);

		JLabel hint = new JLabel("<html>Fill gaps only imports storages Bankless Bank has no data for. "
			+ "Overwrite replaces everything with DWMS's data.</html>");
		hint.setForeground(Color.GRAY);
		hint.setFont(hint.getFont().deriveFont(11f));
		content.add(hint);

		add(content, BorderLayout.NORTH);

		setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 0));

		refresh();
	}

	private void runImport(DwmsImporter.Mode mode)
	{
		fillGapsButton.setEnabled(false);
		overwriteButton.setEnabled(false);
		lastImportLabel.setText("Importing...");

		// The callback already runs on the client thread, which is where reload() has to happen;
		// only the Swing update is bounced to the EDT.
		clientThread.invoke(() -> importer.importNow(mode, result ->
		{
			plugin.reload();
			SwingUtilities.invokeLater(this::refresh);
		}));
	}

	/** Refreshes the panel from the importer's current status. Safe to call off the client thread. */
	public void refresh()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::refresh);
			return;
		}

		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;

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

		boolean enabled = loggedIn && !importer.isImporting();
		fillGapsButton.setEnabled(enabled);
		overwriteButton.setEnabled(enabled);
		fillGapsButton.setHorizontalAlignment(SwingConstants.CENTER);
		overwriteButton.setHorizontalAlignment(SwingConstants.CENTER);
	}
}
