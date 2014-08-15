package quests;

import l2s.gameserver.model.Player;
import l2s.gameserver.model.base.Race;
import l2s.gameserver.model.entity.Reflection;
import l2s.gameserver.model.instances.NpcInstance;
import l2s.gameserver.model.quest.Quest;
import l2s.gameserver.model.quest.QuestState;
import l2s.gameserver.scripts.Functions;
import l2s.gameserver.scripts.ScriptFile;
import l2s.gameserver.network.l2.s2c.ExShowScreenMessage;
import l2s.gameserver.network.l2.s2c.ExShowScreenMessage.ScreenMessageAlign;
import l2s.gameserver.network.l2.s2c.TutorialShowHtmlPacket;
import l2s.gameserver.network.l2.components.ChatType;
import l2s.gameserver.network.l2.components.NpcString;
import l2s.gameserver.utils.Location;
import l2s.gameserver.utils.NpcUtils;
import l2s.gameserver.utils.ReflectionUtils;

//By Evil_dnk dev.fairytale-world.ru
public class _10323_GoingIntoARealWar extends Quest implements ScriptFile
{
	private final static int key = 17574;
	private NpcInstance solderg = null;
	private final static int shenon = 32974;
	private final static int evain = 33464;
	private final static int holden = 33194;
	private final static int guard = 33021;
	private final static int husk = 23113;
	private final static int solder = 33014;

	private static final int[] SOLDER_START_POINT = {-110808, 253896, -1817};

	@Override
	public void onLoad()
	{}

	@Override
	public void onReload()
	{}

	@Override
	public void onShutdown()
	{}

	public _10323_GoingIntoARealWar()
	{
		super(false);
		addStartNpc(evain);
		addTalkId(shenon);
		addTalkId(holden);
		addFirstTalkId(guard);
		addKillId(husk);

		addLevelCheck(1, 20);
		addQuestCompletedCheck(_10322_SearchingForTheMysteriousPower.class);
		addRaceCheck(true, true, true, true, true, true, false);
	}

	private void spawnsolder(QuestState st)
	{
		solderg = NpcUtils.spawnSingle(solder, Location.findPointToStay(SOLDER_START_POINT[0], SOLDER_START_POINT[1], SOLDER_START_POINT[2], 50, 100, st.getPlayer().getGeoIndex()));
		solderg.setFollowTarget(st.getPlayer());
		Functions.npcSay(solderg, NpcString.S1_COME_WITH_ME_I_WILL_LEAD_YOU_TO_HOLDEN, st.getPlayer().getName());
	}

	private void despawnsolder()
	{
		if(solderg != null)
			solderg.deleteMe();
	}

	@Override
	public String onEvent(String event, QuestState st, NpcInstance npc)
	{
		String htmltext = event;
		Player player = st.getPlayer();

		if(event.equalsIgnoreCase("quest_ac"))
		{
			st.giveItems(17574, 1);
			st.setState(STARTED);
			st.setCond(1);
			spawnsolder(st);
			st.playSound(SOUND_ACCEPT);
			htmltext = "0-3.htm";
		}

		if(event.equalsIgnoreCase("qet_rev"))
		{
			htmltext = "3-2.htm";
			st.getPlayer().addExpAndSp(300, 1500);
			st.giveItems(57, 9000);
			st.takeAllItems(17574);
			st.exitCurrentQuest(false);
			st.playSound(SOUND_FINISH);
		}
	
		if(event.equalsIgnoreCase("enter_instance")) {
			if(st.getQuestItemsCount(key) >= 1) {
				despawnsolder();
				st.setCond(2);
				st.playSound(SOUND_MIDDLE);
				enterInstance(st, 176);
				return null;
			} else
				htmltext = "1-1.htm";

		}
		if(event.equalsIgnoreCase("getshots")) {
			st.playSound(SOUND_MIDDLE);
			player.sendPacket(new TutorialShowHtmlPacket(TutorialShowHtmlPacket.LARGE_WINDOW, "..\\L2Text\\QT_003_bullet_01.htm"));
			//st.showTutorialHTML(TutorialShowHtmlPacket.LARGE_WINDOW, "..\\L2Text\\QT_003_bullet_01.htm");
			if(!player.isMageClass() || player.getRace() == Race.ORC)
			{
				player.sendPacket(new ExShowScreenMessage(NpcString.SOULSHOT_HAVE_BEEN_ADDED_TO_YOUR_INVENTORY, 4500, ScreenMessageAlign.TOP_CENTER));
				st.startQuestTimer("soul_timer", 4000);
				st.giveItems(5789, 500);
				st.setCond(5);
			}
			else
			{
				player.sendPacket(new ExShowScreenMessage(NpcString.SPIRITSHOT_HAVE_BEEN_ADDED_TO_YOUR_INVENTORY, 4500, ScreenMessageAlign.TOP_CENTER));
				st.startQuestTimer("spirit_timer", 4000);
				st.giveItems(5790, 500);
				st.setCond(4);
			}
			return null;
		}
		if(event.equalsIgnoreCase("soul_timer"))
		{
			htmltext = "2-3.htm";
			player.sendPacket(new ExShowScreenMessage(NpcString.AUTOMATE_SOULSHOT_AS_SHOWN_IN_THE_TUTORIAL, 4500, ScreenMessageAlign.TOP_CENTER));
		}
		if(event.equalsIgnoreCase("spirit_timer"))
		{
			htmltext = "2-3m.htm";
			player.sendPacket(new ExShowScreenMessage(NpcString.AUTOMATE_SPIRITSHOT_AS_SHOWN_IN_THE_TUTORIAL, 4500, ScreenMessageAlign.TOP_CENTER));
		}
		return htmltext;
	}

	@Override
	public String onTalk(NpcInstance npc, QuestState st) {
		int cond = st.getCond();
		int npcId = npc.getNpcId();
		String htmltext = "noquest";
		if(npcId == evain) 
		{
			if(st.isCompleted())
				htmltext = "0-c.htm";
			QuestState qs = st.getPlayer().getQuestState(_10322_SearchingForTheMysteriousPower.class);
			if(qs == null || !qs.isCompleted())
				return "You should complete another quest to start this!";
			if(st.getPlayer().getLevel() > 20)
				return "Your Level is too high for this quest!";
			else if(cond == 0)
				htmltext = "start.htm";
			else if(cond == 1)
				htmltext = "0-4.htm";
			else if(cond >= 1 && st.getQuestItemsCount(key) < 1) 
			{
				st.giveItems(17574, 1);
				htmltext = "0-5.htm";
			} 
			else if(cond == 8)
				htmltext = "0-6.htm";
			else
				htmltext = "noqu.htm";
		} 
		else if(npcId == shenon)
		{
			if(st.isCompleted())
				htmltext = "3-c.htm";
			else if(cond == 8)
				htmltext = "3-1.htm";
		}
		return htmltext;
	}

	public String onFirstTalk(NpcInstance npc, Player player) 
	{
		QuestState st = player.getQuestState(getClass());
		String htmltext = "";
		if(st != null && npc.getNpcId() == guard) 
		{
			if(st.getCond() == 3) 
			{
				if(!player.isMageClass() || player.getRace() == Race.ORC)
					htmltext = "2-2.htm";
				else
					htmltext = "2-2m.htm";
			} 
			else if(st.getCond() == 4 || st.getCond() == 5) 
			{
				st.setCond(7);
				st.playSound(SOUND_MIDDLE);
				st.getPlayer().getReflection().addSpawnWithoutRespawn(husk, new Location(-115029, 247884, -7872, 0), 0);
				st.getPlayer().getReflection().addSpawnWithoutRespawn(husk, new Location(-114921, 248281, -7872, 0), 0);
				st.getPlayer().getReflection().addSpawnWithoutRespawn(husk, new Location(-114559, 248661, -7872, 0), 0);
				st.getPlayer().getReflection().addSpawnWithoutRespawn(husk, new Location(-114148, 248416, -7872, 0), 0);
				if(!player.isMageClass() || player.getRace() == Race.ORC)
					htmltext = "2-4.htm";
				else
					htmltext = "2-4m.htm";
			} 
			else if(st.getCond() == 8) 
			{
				htmltext = "2-5.htm";
			}
		}
		return htmltext;
	}


	@Override
	public String onKill(NpcInstance npc, QuestState st)
	{
		int npcId = npc.getNpcId();
		int killedhusk = st.getInt("killedhusk");
		if(npcId == husk && (st.getCond() == 2 || st.getCond() == 7))
		{
			if(killedhusk >= 3)
			{
				st.setCond(st.getCond() + 1);
				st.unset("killedhusk");
				st.playSound(SOUND_MIDDLE);
			}
			else
				st.set("killedhusk", ++killedhusk);
		}
		return null;
	}
}