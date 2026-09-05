package io.robrichardson.banklessbank;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BanklessBankPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BanklessBankPlugin.class);
		RuneLite.main(args);
	}
}
