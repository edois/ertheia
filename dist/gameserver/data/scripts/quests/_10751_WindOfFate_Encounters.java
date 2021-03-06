package quests;

import l2s.gameserver.Config;
import l2s.gameserver.instancemanager.QuestManager;
import l2s.gameserver.model.instances.NpcInstance;
import l2s.gameserver.model.items.ItemInstance;
import l2s.gameserver.model.items.PcInventory;
import l2s.gameserver.listener.actor.player.OnLevelChangeListener;
import l2s.gameserver.listener.actor.player.OnPlayerEnterListener;
import l2s.gameserver.model.Creature;
import l2s.gameserver.model.Player;
import l2s.gameserver.model.actor.listener.CharListenerList;
import l2s.gameserver.model.base.ClassId;
import l2s.gameserver.model.base.ClassLevel;
import l2s.gameserver.model.base.Race;
import l2s.gameserver.model.quest.Quest;
import l2s.gameserver.model.quest.QuestState;
import l2s.gameserver.network.l2.components.SystemMsg;
import l2s.gameserver.network.l2.s2c.ExCallToChangeClass;
import l2s.gameserver.network.l2.s2c.ExShowScreenMessage;
import l2s.gameserver.network.l2.s2c.MagicSkillUse;
import l2s.gameserver.network.l2.s2c.SocialActionPacket;
import l2s.gameserver.network.l2.s2c.TutorialCloseHtmlPacket;
import l2s.gameserver.scripts.ScriptFile;
import l2s.gameserver.utils.Language;

/**
 * @author Hien Son
 */

public class _10751_WindOfFate_Encounters extends Quest implements ScriptFile, OnPlayerEnterListener, OnLevelChangeListener
{

	private static final int NAVARI = 33931;
	private static final int KATALIN = 33943;
	private static final int AYANTHE = 33942;
	private static final int RAYMOND = 30289;
	private static final int TELESHA_CORPSE = 33981;
	private static final int MYSTERIOUS_WIZARD = 33980;

	private static final int NAVARI_BOX_MARAUDER = 40266;
	private static final int NAVARI_BOX_WIZARD = 40267;
	private static final int WIND_SPIRIT_RELIC = 39535;

	private static final int SKELETON_WARRIOR = 27528;
	private static final int SKELETON_ARCHER = 27529;
	
	private static final int MARAUDER_CLASS_ID = 184;
	private static final int SAYHA_MAGE_ID = 185;
	
	private static final int minLevel = 38;
	private static final int maxLevel = 99;
	
	private static String LETTER_ALERT_STRING = "Bạn vừa nhận được thư từ Nữ Hoàng Navari";
	private static String CHECK_TELESHA_CORPSE = "Kiểm tra xác của Telesha";
	private static String TALK_TO_WIZARD = "Hãy nói chuyện với Mysterious Wizard";
	private static String RETURN_GLUDIO = "Hãy trở về thị trấn Gludio";

	private static final String SKELETON_KILL_LIST = "skeleton_kill_list";
	
	NpcInstance telesha_corpse_instance = null;
	NpcInstance mysterious_wizard_instance = null;
	
	@Override
	public void onLoad()
	{
	}

	@Override
	public void onReload()
	{
	}

	@Override
	public void onShutdown()
	{
	}
	
	/*
	 cond 1: accept quest, teleport to Faeron, talk to Navari
	 cond 2: find Katalin
	 cond 3: find Ayanthe
	 cond 4: from Katalin to Raymond
	 cond 5: from Ayanthe to Raymond
	 cond 6: kill skeleton to find Telesha's corpse
	 cond 7: find Raymond
	 cond 8: find Katalin
	 cond 9: find Ayanthe
	 */
	
	public _10751_WindOfFate_Encounters()
	{
		super(false);

		CharListenerList.addGlobal(this);
		
		addTalkId(NAVARI, KATALIN, AYANTHE, RAYMOND, TELESHA_CORPSE, MYSTERIOUS_WIZARD);
		
		addKillNpcWithLog(6, SKELETON_KILL_LIST, 5, SKELETON_WARRIOR, SKELETON_ARCHER);
		
		addLevelCheck(minLevel, maxLevel);
		addRaceCheck(false, false, false, false, false, false, true);
		
		if(Config.DEFAULT_LANG != Language.VIETNAMESE)
		{
			LETTER_ALERT_STRING = "You received letter from Queen Navari";
			CHECK_TELESHA_CORPSE = "Check Telesha's corpse";
			TALK_TO_WIZARD = "Talk to Mysterious Wizard";
			RETURN_GLUDIO = "Return to Town of Gludio";
		}
		
	}

	@Override
	public String onEvent(String event, QuestState st, NpcInstance npc)
	{
		Player player = st.getPlayer();
		if(player == null)
			return null;

		String htmltext = event;

		//System.out.println("quest event " + event.toString());
		
		int classId = player.getClassId().getId();
		if(event.startsWith("UC"))
		{
			if(checkStartCondition(player))
			{
				Quest q = QuestManager.getQuest(10751);
				player.processQuestEvent(q.getName(), "start_quest", null);
			}
			
			htmltext = "";
			return null;
		}
		
		if(event.equalsIgnoreCase("start_quest") || event.equalsIgnoreCase("start_quest_7s"))
		{
			st.setCond(1);
			st.setState(STARTED);
			alertLetterReceived(st);
			st.showQuestHTML(st.getQuest(), "queen_letter.htm");
			
			htmltext = "";
			return null;
		}
		
		if(event.equalsIgnoreCase("start_quest_delay"))
		{
			st.startQuestTimer("start_quest_7s", 7000);
			//only start quest after 7s to avoid crash on enterworld
			htmltext = "";
			return null;
		}
		
		if(event.equalsIgnoreCase("Quest _10751_WindOfFate_Encounters to_faeron"))
		{
			if(st.getCond() == 1)
			{
				player.teleToLocation(-80565, 251763, -3080);
				player.sendPacket(TutorialCloseHtmlPacket.STATIC);
				
			}
			htmltext = "";
			return null;
		}
		
		if(event.equalsIgnoreCase("Quest _10751_WindOfFate_Encounters close_window"))
		{
			player.sendPacket(TutorialCloseHtmlPacket.STATIC);
			htmltext = "";
			return null;
		}
		
		// Question mark clicked
		if(event.startsWith("QM"))
		{
			int MarkId = Integer.valueOf(event.substring(2));
			//System.out.println("Mark id " + MarkId);
			if(MarkId == 10751)
			{
				if(player.getRace() == Race.ERTHEIA)
					st.showQuestHTML(st.getQuest(), "queen_letter.htm");
				htmltext = "";
				return null;
			}
		}
		
		if(event.equalsIgnoreCase("33931-2.htm"))
		{
			if(player.isMageClass())
				st.setCond(3);
			else
				st.setCond(2);
		}
		
		if(event.equalsIgnoreCase("33943-2.htm"))
		{
			st.setCond(4);
		}
		
		if(event.equalsIgnoreCase("33942-2.htm"))
		{
			st.setCond(5);
		}
		
		if(event.equalsIgnoreCase("30289-3.htm"))
		{
			st.giveItems(WIND_SPIRIT_RELIC, 1);
			st.setCond(6);
		}
		
		
		if(event.equalsIgnoreCase("check_body"))
		{
			if(telesha_corpse_instance != null)
			{
				//remove telesha corpse
				telesha_corpse_instance.deleteMe();
				//spawn Mysterious Wizard and despawn after 3 minutes
				mysterious_wizard_instance = st.addSpawn(MYSTERIOUS_WIZARD, player.getLoc().getX() + 100, player.getLoc().getY(), player.getLoc().getZ(), 180000);
				
				player.sendPacket(new ExShowScreenMessage(TALK_TO_WIZARD, 7000, ExShowScreenMessage.ScreenMessageAlign.TOP_CENTER, true));
				
			}
			
			return null;
			
		}
		
		if(event.equalsIgnoreCase("33980-2.htm"))
		{
			st.giveItems(WIND_SPIRIT_RELIC, 1);
			player.sendPacket(new ExShowScreenMessage(RETURN_GLUDIO, 7000, ExShowScreenMessage.ScreenMessageAlign.TOP_CENTER, true));
			
			st.setCond(7);
		}
		
		if(event.equalsIgnoreCase("30289-6.htm"))
		{
			if(player.isMageClass())
			{
				htmltext = "30289-7.htm";
				st.setCond(9);
			}
			else
			{
				htmltext = "30289-6.htm";
				st.setCond(8);
			}
		}
		
		if(event.equalsIgnoreCase("33943-10.htm") || event.equalsIgnoreCase("33942-10.htm"))
		{
			int newClassId;
			
			if(player.isMageClass())
			{
				st.giveItems(NAVARI_BOX_WIZARD, 1);
				newClassId = SAYHA_MAGE_ID;
			}
			else
			{
				st.giveItems(NAVARI_BOX_MARAUDER, 1);
				newClassId = MARAUDER_CLASS_ID;
			}
				
			
			st.takeItems(WIND_SPIRIT_RELIC, 2);
			
			
			st.addExpAndSp(2700000, 648);
			st.setState(COMPLETED);
			st.exitCurrentQuest(false);
			st.playSound(SOUND_FINISH);
			
			player.sendPacket(SystemMsg.CONGRATULATIONS__YOUVE_COMPLETED_A_CLASS_TRANSFER);
			player.setClassId(newClassId, false);
			player.broadcastPacket(new SocialActionPacket(player.getObjectId(), 23));
			player.broadcastCharInfo();
		}
		
		return htmltext;
	}


	@Override
	public String onTalk(NpcInstance npc, QuestState st)
	{
		String htmltext = "noquest";
		int npcId = npc.getNpcId();
		int cond = st.getCond();
		if(npcId == NAVARI)
		{
			if(cond == 1)
			{
				htmltext = "33931-1.htm";
			}
			else if(cond == 2)
				htmltext = "33931-2.htm";
			else if(cond == 2)
				htmltext = "33931-3.htm";
		}
		else if(npcId == KATALIN)
		{
			if(cond == 2)
			{
				htmltext = "33943-1.htm";
			}
			else if(cond == 8)
			{
				htmltext = "33943-3.htm";
			}
		}
		else if(npcId == AYANTHE)
		{
			if(cond == 3)
			{
				htmltext = "33942-1.htm";
			}
			else if(cond == 9)
			{
				htmltext = "33942-3.htm";
			}
		}
		else if(npcId == MYSTERIOUS_WIZARD)
		{
			htmltext = "33980-1.htm";
		}
		else if(npcId == RAYMOND)
		{
			if(cond == 4 || cond == 5)
			{
				htmltext = "30289-1.htm";
			}
			else if(cond == 7)
			{
				htmltext = "30289-4.htm";
			}
		}
		return htmltext;
	}
	
	@Override
	public void onPlayerEnter(Player player)
	{
		//if(player.getVarBoolean("@received_navari_letter_3rd"))
			//return;
		
		//System.out.println("Player enter");
		if(checkStartCondition(player))
		{
			//System.out.println("Player enter and fit quest condition");
			Quest q = QuestManager.getQuest(10751);
			player.processQuestEvent(q.getName(), "start_quest_delay", null);
		}
		
	}

	@Override
	public void onLevelChange(Player player, int oldLvl, int newLvl)
	{
		//System.out.println("level change oldLvl " + oldLvl + " newLvl " + newLvl + "checkStartCondition " + checkStartCondition(player));
		if(oldLvl < minLevel && newLvl >= minLevel && checkStartCondition(player))
		{
			//System.out.println("received_navari_letter_3rd " + player.getVarBoolean("@received_navari_letter_3rd"));
			//if(player.getVarBoolean("@received_navari_letter_3rd"))
				//return;

			Quest q = QuestManager.getQuest(10751);
			player.processQuestEvent(q.getName(), "start_quest", null);
			
		}
	}

	@Override
	public String onKill(NpcInstance npc, QuestState st)
	{
		int npcId = npc.getNpcId();
		int cond = st.getCond();
		Player player = st.getPlayer();
		
		/*TODO
		 * Sniff packet after kill in this quest, make another packet for notify the counter in client
		 * the current packet ExQuestNpcLogList doesn't work
		 */
		
		if(	npcId == SKELETON_WARRIOR || npcId == SKELETON_ARCHER)
			{
				int count = st.getInt(SKELETON_KILL_LIST);
				count++;
				if(Config.DEFAULT_LANG == Language.VIETNAMESE)
				{
					st.getPlayer().sendPacket(new ExShowScreenMessage("Bạn tiêu diệt được " + count + " Skeleton", 2000, ExShowScreenMessage.ScreenMessageAlign.BOTTOM_RIGHT, false));
				}
				else
				{
					st.getPlayer().sendPacket(new ExShowScreenMessage("You have killed " + count + " Skeleton", 2000, ExShowScreenMessage.ScreenMessageAlign.BOTTOM_RIGHT, false));
				}
				
			}
		if(updateKill(npc, st))
		{
			st.playSound(SOUND_MIDDLE);
			
			//spawn Telesha's corpse and despawn after 3 minutes
			telesha_corpse_instance = st.addSpawn(TELESHA_CORPSE, npc.getLoc().getX(), npc.getLoc().getY(), npc.getLoc().getZ(), 180000);
			
			player.sendPacket(new ExShowScreenMessage(CHECK_TELESHA_CORPSE, 7000, ExShowScreenMessage.ScreenMessageAlign.TOP_CENTER, true));
			
		}
		
		return null;
	}
	
	private void alertLetterReceived(QuestState st)
	{
		if(st == null) return;
		
		st.getPlayer().sendPacket(new ExShowScreenMessage(LETTER_ALERT_STRING, 7000, ExShowScreenMessage.ScreenMessageAlign.TOP_CENTER, true));
		
		st.showQuestionMark(10751);
		
		st.playSound(SOUND_TUTORIAL);
		
		st.getPlayer().setVar("@received_navari_letter_3rd", true);
	}
	
	@Override
	public boolean checkStartCondition(Player player)
	{
		QuestState st = player.getQuestState("_10751_WindOfFate_Encounters");
		
		boolean result = (player.getLevel() >= minLevel && 
				player.getLevel() <= maxLevel && 
				player.getRace() == Race.ERTHEIA && 
				(player.getClassId() == ClassId.ERTHEIA_FIGHTER || player.getClassId() == ClassId.ERTHEIA_MAGE ) && 
				(st == null || (st != null && st.getCond() == 0)));
		
		//System.out.println("checkStartCondition Q10752 " + result);
		return result;
	}

}