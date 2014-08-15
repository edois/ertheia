package l2s.gameserver.scripts;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import l2s.commons.lang.reference.HardReference;
import l2s.commons.lang.reference.HardReferences;
import l2s.commons.threading.RunnableImpl;
import l2s.gameserver.Config;
import l2s.gameserver.ThreadPoolManager;
import l2s.gameserver.data.xml.holder.NpcHolder;
import l2s.gameserver.instancemanager.ReflectionManager;
import l2s.gameserver.instancemanager.ServerVariables;
import l2s.gameserver.model.Creature;
import l2s.gameserver.model.GameObjectsStorage;
import l2s.gameserver.model.Playable;
import l2s.gameserver.model.Player;
import l2s.gameserver.model.SimpleSpawner;
import l2s.gameserver.model.World;
import l2s.gameserver.model.entity.Reflection;
import l2s.gameserver.model.instances.NpcInstance;
import l2s.gameserver.model.instances.PetInstance;
import l2s.gameserver.model.items.ItemInstance;
import l2s.gameserver.model.mail.Mail;
import l2s.gameserver.network.l2.components.ChatType;
import l2s.gameserver.network.l2.components.CustomMessage;
import l2s.gameserver.network.l2.components.NpcString;
import l2s.gameserver.network.l2.components.SystemMsg;
import l2s.gameserver.network.l2.s2c.ExNoticePostArrived;
import l2s.gameserver.network.l2.s2c.ExUnReadMailCount;
import l2s.gameserver.network.l2.s2c.NpcHtmlMessagePacket;
import l2s.gameserver.network.l2.s2c.NSPacket;
import l2s.gameserver.templates.npc.NpcTemplate;
import l2s.gameserver.utils.HtmlUtils;
import l2s.gameserver.utils.ItemFunctions;
import l2s.gameserver.utils.Location;
import l2s.gameserver.utils.MapUtils;
import l2s.gameserver.utils.NpcUtils;

/**
 * @Author: Diamond
 * @Date: 7/6/2007
 * @Time: 5:22:23
 */
public class Functions
{
	public HardReference<Player> self = HardReferences.emptyRef();
	public HardReference<NpcInstance> npc = HardReferences.emptyRef();

	/**
	 * Вызывает метод с задержкой
	 *
	 * @param object	 - от чьего имени вызывать
	 * @param sClass<?>  - вызываемый класс
	 * @param methodName - вызываемый метод
	 * @param args	   - массив аргуметов
	 * @param variables  - список выставляемых переменных
	 * @param delay	  - задержка в миллисекундах
	 */
	public static ScheduledFuture<?> executeTask(final Player caller, final String className, final String methodName, final Object[] args, final Map<String, Object> variables, long delay)
	{
		return ThreadPoolManager.getInstance().schedule(new RunnableImpl(){
			@Override
			public void runImpl() throws Exception
			{
				callScripts(caller, className, methodName, args, variables);
			}
		}, delay);
	}

	public static ScheduledFuture<?> executeTask(String className, String methodName, Object[] args, Map<String, Object> variables, long delay)
	{
		return executeTask(null, className, methodName, args, variables, delay);
	}

	public static ScheduledFuture<?> executeTask(Player player, String className, String methodName, Object[] args, long delay)
	{
		return executeTask(player, className, methodName, args, null, delay);
	}

	public static ScheduledFuture<?> executeTask(String className, String methodName, Object[] args, long delay)
	{
		return executeTask(className, methodName, args, null, delay);
	}

	public static Object callScripts(String className, String methodName, Object[] args)
	{
		return callScripts(className, methodName, args, null);
	}

	public static Object callScripts(String className, String methodName, Object[] args, Map<String, Object> variables)
	{
		return callScripts(null, className, methodName, args, variables);
	}

	public static Object callScripts(Player player, String className, String methodName, Object[] args, Map<String, Object> variables)
	{
		return Scripts.getInstance().callScripts(player, className, methodName, args, variables);
	}

	/**
	 * Вызывать только из скриптов
	 */
	public void show(String text, Player self)
	{
		show(text, self, getNpc());
	}

	public static void show(CustomMessage message, Player self)
	{
		show(message.toString(), self, null);
	}

	public static void show(String text, Player self, int itemId, Object... arg)
	{
		if(text == null || self == null)
			return;

		NpcHtmlMessagePacket msg = new NpcHtmlMessagePacket(self, null);

		// приводим нашу html-ку в нужный вид
		if(text.endsWith(".html") || text.endsWith(".htm"))
			msg.setFile(text);
		else
			msg.setHtml(HtmlUtils.bbParse(text));

		if(arg != null && arg.length % 2 == 0)
			for(int i = 0; i < arg.length; i = +2)
				msg.replace(String.valueOf(arg[i]), String.valueOf(arg[i + 1]));

		msg.setItemId(itemId);
		self.sendPacket(msg);
	}

	/**
	 * Статический метод, для вызова из любых мест
	 */
	public static void show(String text, Player self, NpcInstance npc, Object... arg)
	{
		if(text == null || self == null)
			return;

		NpcHtmlMessagePacket msg = new NpcHtmlMessagePacket(self, npc);

		// приводим нашу html-ку в нужный вид
		if(text.endsWith(".html") || text.endsWith(".htm"))
			msg.setFile(text);
		else
			msg.setHtml(HtmlUtils.bbParse(text));

		if(arg != null && arg.length % 2 == 0)
			for(int i = 0; i < arg.length; i = +2)
				msg.replace(String.valueOf(arg[i]), String.valueOf(arg[i + 1]));

		self.sendPacket(msg);
	}

	public static void sendMessage(String text, Player self)
	{
		self.sendMessage(text);
	}

	public static void sendMessage(CustomMessage message, Player self)
	{
		self.sendMessage(message);
	}

	// Белый чат
	public static void npcSayInRange(NpcInstance npc, String text, int range)
	{
		npcSayInRange(npc, range, NpcString.NONE, text);
	}

	// Белый чат
	public static void npcSayInRange(NpcInstance npc, int range, NpcString fStringId, String... params)
	{
		if(npc == null)
			return;
		NSPacket cs = new NSPacket(npc, ChatType.NPC_ALL, fStringId, params);
		for(Player player : World.getAroundPlayers(npc, range, Math.max(range / 2, 200)))
			if(npc.getReflection() == player.getReflection())
				player.sendPacket(cs);
	}

	// Белый чат
	public static void npcSay(NpcInstance npc, String text)
	{
		npcSayInRange(npc, text, 1500);
	}

	// Белый чат
	public static void npcSay(NpcInstance npc, NpcString npcString, String... params)
	{
		npcSayInRange(npc, 1500, npcString, params);
	}

	// Белый чат
	public static void npcSayInRangeCustomMessage(NpcInstance npc, int range, String address, Object... replacements)
	{
		if(npc == null)
			return;
		for(Player player : World.getAroundPlayers(npc, range, Math.max(range / 2, 200)))
			if(npc.getReflection() == player.getReflection())
				player.sendPacket(new NSPacket(npc, ChatType.NPC_ALL, new CustomMessage(address, player, replacements).toString()));
	}

	// Белый чат
	public static void npcSayCustomMessage(NpcInstance npc, String address, Object... replacements)
	{
		npcSayInRangeCustomMessage(npc, 1500, address, replacements);
	}

	// private message
	public static void npcSayToPlayer(NpcInstance npc, Player player, String text)
	{
		npcSayToPlayer(npc, player, NpcString.NONE, text);
	}

	// private message
	public static void npcSayToPlayer(NpcInstance npc, Player player, NpcString npcString, String... params)
	{
		if(npc == null)
			return;
		player.sendPacket(new NSPacket(npc, ChatType.TELL, npcString, params));
	}

	// Shout (желтый) чат
	public static void npcShout(NpcInstance npc, String text)
	{
		npcShout(npc, NpcString.NONE, text);
	}

	// Shout (желтый) чат
	public static void npcShout(NpcInstance npc, NpcString npcString, String... params)
	{
		if(npc == null)
			return;
		NSPacket cs = new NSPacket(npc, ChatType.NPC_SHOUT, npcString, params);

		int rx = MapUtils.regionX(npc);
		int ry = MapUtils.regionY(npc);
		int offset = Config.SHOUT_OFFSET;

		for(Player player : GameObjectsStorage.getAllPlayersForIterate())
		{
			if(player.getReflection() != npc.getReflection())
				continue;

			int tx = MapUtils.regionX(player);
			int ty = MapUtils.regionY(player);

			if(tx >= rx - offset && tx <= rx + offset && ty >= ry - offset && ty <= ry + offset)
				player.sendPacket(cs);
		}
	}

	// Shout (желтый) чат
	public static void npcShoutCustomMessage(NpcInstance npc, String address, Object... replacements)
	{
		if(npc == null)
			return;

		int rx = MapUtils.regionX(npc);
		int ry = MapUtils.regionY(npc);
		int offset = Config.SHOUT_OFFSET;

		for(Player player : GameObjectsStorage.getAllPlayersForIterate())
		{
			if(player.getReflection() != npc.getReflection())
				continue;

			int tx = MapUtils.regionX(player);
			int ty = MapUtils.regionY(player);

			if(tx >= rx - offset && tx <= rx + offset && ty >= ry - offset && ty <= ry + offset || npc.isInRange(player, Config.CHAT_RANGE))
				player.sendPacket(new NSPacket(npc, ChatType.NPC_SHOUT, new CustomMessage(address, player, replacements).toString()));
		}
	}

	public static void npcSay(NpcInstance npc, NpcString address, ChatType type, int range, String... replacements)
	{
		if(npc == null)
			return;
		for(Player player : World.getAroundPlayers(npc, range, Math.max(range / 2, 200)))
		{
			if(player.getReflection() == npc.getReflection())
				player.sendPacket(new NSPacket(npc, type, address, replacements));
		}
	}

	/**
	 * @see ItemFunctions
	 */
	public static void addItem(Playable playable, int itemId, long count)
	{
		ItemFunctions.addItem(playable, itemId, count, true);
	}

	/**
	 * @see ItemFunctions
	 */
	public static long getItemCount(Playable playable, int itemId)
	{
		return ItemFunctions.getItemCount(playable, itemId);
	}

	/**
	 * @see ItemFunctions
	 */
	public static long removeItem(Playable playable, int itemId, long count)
	{
		return ItemFunctions.removeItem(playable, itemId, count, true);
	}

	public static boolean ride(Player player, int npcId)
	{
		if(player.getServitors().length > 0)
		{
			player.sendPacket(SystemMsg.YOU_ALREADY_HAVE_A_PET);
			return false;
		}

		player.setMount(0, npcId, player.getLevel(), -1);
		return true;
	}

	public static void unRide(Player player)
	{
		if(player.isMounted())
			player.setMount(null);
	}

	public static void unSummonPet(Player player, boolean onlyPets)
	{
		PetInstance pet = player.getPet();
		if(pet == null)
			return;
		if(!onlyPets)
			pet.unSummon(false);
	}

	//@Deprecated
	//TODO [VISTALL] use NpcUtils
	public static NpcInstance spawn(Location loc, int npcId)
	{
		return spawn(loc, npcId, ReflectionManager.DEFAULT);
	}

	//@Deprecated
	public static NpcInstance spawn(Location loc, int npcId, Reflection reflection)
	{
		return NpcUtils.spawnSingle(npcId, loc, reflection, 0);
	}

	public Player getSelf()
	{
		return self.get();
	}

	public NpcInstance getNpc()
	{
		return npc.get();
	}

	//@Deprecated
	//TODO [VISTALL] use NpcUtils
	public static void SpawnNPCs(int npcId, int[][] locations, List<SimpleSpawner> list)
	{
		NpcTemplate template = NpcHolder.getInstance().getTemplate(npcId);
		if(template == null)
		{
			System.out.println("WARNING! Functions.SpawnNPCs template is null for npc: " + npcId);
			Thread.dumpStack();
			return;
		}
		for(int[] location : locations)
		{
			SimpleSpawner sp = new SimpleSpawner(template);
			sp.setLoc(new Location(location[0], location[1], location[2]));
			sp.setAmount(1);
			sp.setRespawnDelay(0);
			sp.init();
			if(list != null)
				list.add(sp);
		}
	}

	public static void deSpawnNPCs(List<SimpleSpawner> list)
	{
		for(SimpleSpawner sp : list)
			sp.deleteAll();

		list.clear();
	}

	public static boolean IsActive(String name)
	{
		return ServerVariables.getString(name, "off").equalsIgnoreCase("on");
	}

	public static boolean SetActive(String name, boolean active)
	{
		if(active == IsActive(name))
			return false;
		if(active)
			ServerVariables.set(name, "on");
		else
			ServerVariables.unset(name);
		return true;
	}

	public static boolean SimpleCheckDrop(Creature mob, Creature killer)
	{
		return mob != null && mob.isMonster() && !mob.isRaid() && killer != null && killer.getPlayer() != null && killer.getLevel() - mob.getLevel() < 9;
	}

	public static boolean isPvPEventStarted()
	{
		if((Boolean) callScripts("events.TvT.TvT", "isRunned", new Object[] {}))
			return true;
		if((Boolean) callScripts("events.lastHero.LastHero", "isRunned", new Object[] {}))
			return true;
		if((Boolean) callScripts("events.CtF.CtF", "isRunned", new Object[] {}))
			return true;			
		return false;
	}

	public static boolean isEventStarted(String event)
	{
		if((Boolean) callScripts(event, "isRunned", new Object[] {}))
			return true;
		return false;
	}
	
	public static void sendDebugMessage(Player player, String message)
	{
		if(!player.isGM())
			return;
		player.sendMessage(message);
	}

	public static void sendSystemMail(Player receiver, String title, String body, Map<Integer, Long> items)
	{
		if(receiver == null || !receiver.isOnline())
			return;
		if(title == null)
			return;
		if(items.keySet().size() > 8)
			return;

		Mail mail = new Mail();
		mail.setSenderId(1);
		mail.setSenderName("Admin");
		mail.setReceiverId(receiver.getObjectId());
		mail.setReceiverName(receiver.getName());
		mail.setTopic(title);
		mail.setBody(body);
		for(Map.Entry<Integer, Long> itm : items.entrySet())
		{
			ItemInstance item = ItemFunctions.createItem(itm.getKey());
			item.setLocation(ItemInstance.ItemLocation.MAIL);
			item.setCount(itm.getValue());
			item.save();
			mail.addAttachment(item);
		}
		mail.setType(Mail.SenderType.NEWS_INFORMER);
		mail.setUnread(true);
		mail.setExpireTime(720 * 3600 + (int) (System.currentTimeMillis() / 1000L));
		mail.save();

		receiver.sendPacket(ExNoticePostArrived.STATIC_TRUE);
		receiver.sendPacket(new ExUnReadMailCount(receiver));
		receiver.sendPacket(SystemMsg.THE_MAIL_HAS_ARRIVED);
	}
}