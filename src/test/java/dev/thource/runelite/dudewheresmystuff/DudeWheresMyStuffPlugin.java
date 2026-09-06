package dev.thource.runelite.dudewheresmystuff;

import net.runelite.client.plugins.Plugin;

/**
 * Test-only stand-in for the real DWMS plugin class. {@link
 * io.robrichardson.banklessbank.bootstrap.DwmsImporter#findDwmsPlugin()} identifies DWMS by its
 * fully-qualified class name ({@code DwmsImporter.DWMS_PLUGIN_CLASS}); this class exists solely
 * so tests can put a plugin with that exact name into a mocked {@code PluginManager} without
 * depending on the real DWMS jar.
 */
public class DudeWheresMyStuffPlugin extends Plugin
{
}
