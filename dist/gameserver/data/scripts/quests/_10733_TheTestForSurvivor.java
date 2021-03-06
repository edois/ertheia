package quests;

import l2s.gameserver.model.Player;
import l2s.gameserver.model.base.ClassId;
import l2s.gameserver.model.instances.NpcInstance;
import l2s.gameserver.model.quest.Quest;
import l2s.gameserver.model.quest.QuestState;
import l2s.gameserver.scripts.ScriptFile;

/**
 * @author Hien Son
 */
public class _10733_TheTestForSurvivor extends Quest implements ScriptFile
{
	//NPC's
	private static final int GERETH = 33932;
	private static final int DIA = 34005;
	private static final int AYANTHE = 33942;
	private static final int KATALIN = 33943;
	
	private static final int GERETH_RECOM = 39519;

	public _10733_TheTestForSurvivor()
	{
		super(false);
		addStartNpc(GERETH);
		addTalkId(GERETH, DIA, KATALIN, AYANTHE);
		
		addLevelCheck(1, 20);
		addQuestCompletedCheck(_10732_AForeignLand.class);
		addRaceCheck(false, false, false, false, false, false, true);
	}

	@Override
	public String onEvent(String event, QuestState st, NpcInstance npc)
	{
		//String htmltext = event;
		Player player = st.getPlayer();
		
		if(event.equalsIgnoreCase("33932-2.htm"))
		{
			st.setCond(1);
			st.setState(STARTED);
			st.playSound(SOUND_ACCEPT);
			st.giveItems(GERETH_RECOM, 1);
		}
		else if(event.equalsIgnoreCase("34005-3.htm"))
		{
			if(player.isMageClass())
				st.setCond(3);
			else 
				st.setCond(2);
		}
		else if(event.equalsIgnoreCase("33943-2.htm") || event.equalsIgnoreCase("33942-2.htm"))
		{
			st.takeItems(GERETH_RECOM, 1);
			st.giveItems(ADENA_ID, 5000, true);
			st.addExpAndSp(295, 2);
			st.setState(COMPLETED);
			st.exitCurrentQuest(false);
			st.playSound(SOUND_FINISH);
		}
		return event;
	}

	@Override
	public String onTalk(NpcInstance npc, QuestState st)
	{
		String htmltext = "noquest";
		int npcId = npc.getNpcId();
		int cond = st.getCond();
		Player player = st.getPlayer();
		
		if(npcId == GERETH)
		{
			if(cond == 0)
			{
				if(checkStartCondition(st.getPlayer()))
				{
					htmltext = "33932-1.htm";
				}
				else
					htmltext = "noquest";
			}
			else if(cond == 1)
				htmltext = "33932-2.htm";
		}
		else if(npcId == DIA && st.getCond() == 1)
		{
			htmltext = "34005-1.htm";
		}
		else if(npcId == KATALIN && st.getCond() == 2 && !player.isMageClass())
		{
			htmltext = "33943-1.htm";
			
		}
		else if(npcId == AYANTHE && st.getCond() == 3 && player.isMageClass())
		{
			htmltext = "33942-1.htm";
			
		}
		return htmltext;
	}

	@Override
	public boolean checkStartCondition(Player player)
	{
		QuestState qs = player.getQuestState(_10732_AForeignLand.class);
		return player.getLevel() <= 20 && qs != null && qs.getState() == COMPLETED;
	}

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
}