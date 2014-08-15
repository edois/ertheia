package l2s.gameserver.model.actor.instances.player;

import gnu.trove.iterator.TIntLongIterator;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.hash.TIntLongHashMap;

import org.apache.commons.lang3.StringUtils;

import l2s.gameserver.Config;
import l2s.gameserver.model.Player;
import l2s.gameserver.network.l2.components.CustomMessage;

public class AntiFlood
{
	private final TIntLongMap _recentReceivers = new TIntLongHashMap();

	private final Player _owner;

	private long _lastSent = 0L;
	private String _lastText = StringUtils.EMPTY;

	private long _allChatUseTime;
	private long _shoutChatUseTime;
	private long _tradeChatUseTime;
	private long _heroChatUseTime;
	private long _privateChatUseTime;
	private long _mailUseTime;
	private long _lastLfcTime;

	public AntiFlood(Player owner)
	{
		_owner = owner;
	}

	public boolean canAll(String text)
	{
		if(_owner.isGM())
			return true;

		if(Config.ALL_CHAT_USE_MIN_LEVEL > _owner.getLevel())
		{
			_owner.sendMessage(new CustomMessage("antispam.no_chat.all.level", _owner).add(Config.ALL_CHAT_USE_MIN_LEVEL).toString());
			return false;
		}

		if(Config.ALL_CHAT_USE_DELAY > 0)
		{
			long currentMillis = System.currentTimeMillis();

			int delay = (int) ((_allChatUseTime - currentMillis) / 1000L);
			if(delay > 0)
			{
				_owner.sendMessage(new CustomMessage("antispam.no_chat.all.delay", _owner).add(delay).toString());
				return false;
			}

			_allChatUseTime = currentMillis + Config.ALL_CHAT_USE_DELAY * 1000L;
		}
		return true;
	}

	public boolean canShout(String text)
	{
		if(_owner.isGM())
			return true;

		if(Config.SHOUT_CHAT_USE_MIN_LEVEL > _owner.getLevel())
		{
			_owner.sendMessage(new CustomMessage("antispam.no_chat.shout.level", _owner).add(Config.SHOUT_CHAT_USE_MIN_LEVEL).toString());
			return false;
		}

		if(Config.SHOUT_CHAT_USE_DELAY > 0)
		{
			long currentMillis = System.currentTimeMillis();

			int delay = (int) ((_shoutChatUseTime - currentMillis) / 1000L);
			if(delay > 0)
			{
				_owner.sendMessage(new CustomMessage("antispam.no_chat.shout.delay", _owner).add(delay).toString());
				return false;
			}

			_shoutChatUseTime = currentMillis + Config.SHOUT_CHAT_USE_DELAY * 1000L;
		}
		return true;
	}

	public boolean canTrade(String text)
	{
		if(_owner.isGM())
			return true;

		if(Config.TRADE_CHAT_USE_MIN_LEVEL > _owner.getLevel())
		{
			_owner.sendMessage(new CustomMessage("antispam.no_chat.trade.level", _owner).add(Config.TRADE_CHAT_USE_MIN_LEVEL).toString());
			return false;
		}

		if(Config.TRADE_CHAT_USE_DELAY > 0)
		{
			long currentMillis = System.currentTimeMillis();

			int delay = (int) ((_tradeChatUseTime - currentMillis) / 1000L);
			if(delay > 0)
			{
				_owner.sendMessage(new CustomMessage("antispam.no_chat.trade.delay", _owner).add(delay).toString());
				return false;
			}

			_tradeChatUseTime = currentMillis + Config.TRADE_CHAT_USE_DELAY * 1000L;
		}
		return true;
	}

	public boolean canHero(String text)
	{
		if(_owner.isGM())
			return true;

		if(Config.HERO_CHAT_USE_MIN_LEVEL > _owner.getLevel())
		{
			_owner.sendMessage(new CustomMessage("antispam.no_chat.hero.level", _owner).add(Config.HERO_CHAT_USE_MIN_LEVEL).toString());
			return false;
		}

		if(Config.HERO_CHAT_USE_DELAY > 0)
		{
			long currentMillis = System.currentTimeMillis();

			int delay = (int) ((_heroChatUseTime - currentMillis) / 1000L);
			if(delay > 0)
			{
				_owner.sendMessage(new CustomMessage("antispam.no_chat.hero.delay", _owner).add(delay).toString());
				return false;
			}

			_heroChatUseTime = currentMillis + Config.HERO_CHAT_USE_DELAY * 1000L;
		}
		return true;
	}

	public boolean canMail()
	{
		if(_owner.isGM())
			return true;

		if(Config.MAIL_USE_MIN_LEVEL > _owner.getLevel())
		{
			_owner.sendMessage(new CustomMessage("antispam.no_mail.level", _owner).add(Config.MAIL_USE_MIN_LEVEL).toString());
			return false;
		}

		if(Config.MAIL_USE_DELAY > 0)
		{
			long currentMillis = System.currentTimeMillis();

			int delay = (int) ((_mailUseTime - currentMillis) / 1000L);
			if(delay > 0)
			{
				_owner.sendMessage(new CustomMessage("antispam.no_mail.delay", _owner).add(delay).toString());
				return false;
			}

			_mailUseTime = currentMillis + Config.MAIL_USE_DELAY * 1000L;
		}
		return true;
	}

	public boolean canTell(int receiverId, String text)
	{
		if(_owner.isGM())
			return true;

		if(Config.PRIVATE_CHAT_USE_MIN_LEVEL > _owner.getLevel())
		{
			_owner.sendMessage(new CustomMessage("antispam.no_chat.private.level", _owner).add(Config.PRIVATE_CHAT_USE_MIN_LEVEL).toString());
			return false;
		}

		if(Config.PRIVATE_CHAT_USE_DELAY > 0)
		{
			long currentMillis = System.currentTimeMillis();

			int delay = (int) ((_privateChatUseTime - currentMillis) / 1000L);
			if(delay > 0)
			{
				_owner.sendMessage(new CustomMessage("antispam.no_chat.private.delay", _owner).add(delay).toString());
				return false;
			}

			_privateChatUseTime = currentMillis + Config.PRIVATE_CHAT_USE_DELAY * 1000L;
		}

		long currentMillis = System.currentTimeMillis();
		long lastSent;

		TIntLongIterator itr = _recentReceivers.iterator();

		int recent = 0;
		while(itr.hasNext())
		{
			itr.advance();
			lastSent = itr.value();
			if(currentMillis - lastSent < (text.equalsIgnoreCase(_lastText) ? 600000L : 60000L))
				recent++;
			else
				itr.remove();
		}

		lastSent = _recentReceivers.put(receiverId, currentMillis);

		long delay = 333L;
		if(recent > 3)
		{
			lastSent = _lastSent;
			delay = (recent - 3) * 3333L;
		}

		_lastText = text;
		_lastSent = currentMillis;

		int remainingDelay = (int) ((delay - (currentMillis - lastSent)) / 1000L);
		if(remainingDelay > 0)
		{
			_owner.sendMessage(new CustomMessage("antispam.no_chat.private.delay", _owner).add(remainingDelay).toString());
			return false;
		}
		return true;
	}
	
	public boolean canLfcChoose()
	{
		long currentMillis = System.currentTimeMillis();

		if (currentMillis - _lastLfcTime < 3*60000)
			return false;

		_lastLfcTime = currentMillis;
		return true;	
	}	
}
