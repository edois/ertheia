package l2s.gameserver.network.l2.c2s;

import java.util.ArrayList;
import java.util.List;

import l2s.commons.math.SafeMath;
import l2s.commons.util.Rnd;
import l2s.gameserver.Config;
import l2s.gameserver.data.xml.holder.ItemHolder;
import l2s.gameserver.data.xml.holder.MultiSellHolder;
import l2s.gameserver.instancemanager.ReflectionManager;
import l2s.gameserver.model.MultiSellListContainer;
import l2s.gameserver.model.MultiSellListContainer.MultisellType;
import l2s.gameserver.model.Player;
import l2s.gameserver.model.base.MultiSellEntry;
import l2s.gameserver.model.base.MultiSellIngredient;
import l2s.gameserver.model.entity.residence.Castle;
import l2s.gameserver.model.instances.NpcInstance;
import l2s.gameserver.model.items.ItemAttributes;
import l2s.gameserver.model.items.ItemInstance;
import l2s.gameserver.model.items.PcInventory;
import l2s.gameserver.network.l2.components.SystemMsg;
import l2s.gameserver.network.l2.s2c.SystemMessage;
import l2s.gameserver.network.l2.s2c.SystemMessagePacket;
import l2s.gameserver.templates.item.ItemTemplate;
import l2s.gameserver.utils.ItemFunctions;
import l2s.gameserver.utils.Log;

public class RequestMultiSellChoose extends L2GameClientPacket
{
	private static final int BUY_DELAY = 200;

	private int _listId;
	private int _entryId;
	private long _amount;

	private class ItemData
	{
		private final int _id;
		private final long _count;
		private final ItemInstance _item;

		public ItemData(int id, long count, ItemInstance item)
		{
			_id = id;
			_count = count;
			_item = item;
		}

		public int getId()
		{
			return _id;
		}

		public long getCount()
		{
			return _count;
		}

		public ItemInstance getItem()
		{
			return _item;
		}

		@Override
		public boolean equals(Object obj)
		{
			if(!(obj instanceof ItemData))
				return false;

			ItemData i = (ItemData) obj;

			return _id == i._id && _count == i._count && _item == i._item;
		}
	}

	@Override
	protected void readImpl()
	{
		_listId = readD();
		_entryId = readD();
		_amount = readQ();
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null || _amount < 1)
			return;

		MultiSellListContainer list1 = activeChar.getMultisell();
		if(list1 == null)
		{
			activeChar.sendActionFailed();
			activeChar.setMultisell(null);
			return;
		}

		// Проверяем, не подменили ли id
		if(list1.getListId() != _listId)
		{
			//TODO audit
			activeChar.sendActionFailed();
			activeChar.setMultisell(null);
			return;
		}

		if(activeChar.isActionsDisabled())
		{
			activeChar.sendActionFailed();
			return;
		}

		if(activeChar.isInStoreMode())
		{
			activeChar.sendPacket(SystemMsg.WHILE_OPERATING_A_PRIVATE_STORE_OR_WORKSHOP_YOU_CANNOT_DISCARD_DESTROY_OR_TRADE_AN_ITEM);
			return;
		}

		if(activeChar.isInTrade())
		{
			activeChar.sendActionFailed();
			return;
		}

		if(activeChar.isFishing())
		{
			activeChar.sendPacket(SystemMsg.YOU_CANNOT_DO_THAT_WHILE_FISHING);
			return;
		}

		if(!Config.ALT_GAME_KARMA_PLAYER_CAN_SHOP && activeChar.isPK() && !activeChar.isGM())
		{
			activeChar.sendActionFailed();
			return;
		}

		if(System.currentTimeMillis() <= (activeChar.getLastMultisellBuyTime() + BUY_DELAY))
		{
			activeChar.sendActionFailed();
			return;
		}

		MultiSellEntry entry = null;
		for(MultiSellEntry $entry : list1.getEntries())
			if($entry.getEntryId() == _entryId)
			{
				entry = $entry;
				break;
			}

		if(entry == null)
			return;

		final boolean keepenchant = list1.isKeepEnchant();
		final boolean notax = list1.isNoTax();
		final List<ItemData> items = new ArrayList<ItemData>();

		PcInventory inventory = activeChar.getInventory();

		long totalPrice = 0;

		NpcInstance merchant = activeChar.getLastNpc();
		Castle castle = merchant != null ? merchant.getCastle(activeChar) : null;

		inventory.writeLock();
		try
		{
			long tax = SafeMath.mulAndCheck(entry.getTax(), _amount);

			long slots = 0;
			long weight = 0;
			for(MultiSellIngredient i : entry.getProduction())
			{
				if(i.getItemId() <= 0)
					continue;
				ItemTemplate item = ItemHolder.getInstance().getTemplate(i.getItemId());

				weight = SafeMath.addAndCheck(weight, SafeMath.mulAndCheck(SafeMath.mulAndCheck(i.getItemCount(), _amount), item.getWeight()));
				if(item.isStackable())
				{
					if(inventory.getItemByItemId(i.getItemId()) == null)
						slots++;
				}
				else
					slots = SafeMath.addAndCheck(slots, _amount);
			}

			if(!inventory.validateWeight(weight))
			{
				activeChar.sendPacket(SystemMsg.YOU_HAVE_EXCEEDED_THE_WEIGHT_LIMIT);
				activeChar.sendActionFailed();
				return;
			}

			if(!inventory.validateCapacity(slots))
			{
				activeChar.sendPacket(SystemMsg.YOUR_INVENTORY_IS_FULL);
				activeChar.sendActionFailed();
				return;
			}

			if(entry.getIngredients().size() == 0)
			{
				activeChar.sendActionFailed();
				activeChar.setMultisell(null);
				return;
			}

			// Перебор всех ингридиентов, проверка наличия и создание списка забираемого
			for(MultiSellIngredient ingridient : entry.getIngredients())
			{
				int ingridientItemId = ingridient.getItemId();
				long ingridientItemCount = ingridient.getItemCount();
				int ingridientEnchant = ingridient.getItemEnchant();
				long totalAmount = !ingridient.getMantainIngredient() ? SafeMath.mulAndCheck(ingridientItemCount, _amount) : ingridientItemCount;

				if(ingridientItemId == ItemTemplate.ITEM_ID_CLAN_REPUTATION_SCORE)
				{
					if(activeChar.getClan() == null)
					{
						activeChar.sendPacket(SystemMsg.YOU_ARE_NOT_A_CLAN_MEMBER_AND_CANNOT_PERFORM_THIS_ACTION);
						return;
					}

					if(activeChar.getClan().getReputationScore() < totalAmount)
					{
						activeChar.sendPacket(SystemMsg.THE_CLAN_REPUTATION_SCORE_IS_TOO_LOW);
						return;
					}

					if(activeChar.getClan().getLeaderId() != activeChar.getObjectId())
					{
						activeChar.sendPacket(new SystemMessagePacket(SystemMsg.S1_IS_NOT_A_CLAN_LEADER).addName(activeChar));
						return;
					}
					if(!ingridient.getMantainIngredient())
						items.add(new ItemData(ingridientItemId, totalAmount, null));
				}
				else if(ingridientItemId == ItemTemplate.ITEM_ID_PC_BANG_POINTS)
				{
					if(activeChar.getPcBangPoints() < totalAmount)
					{
						activeChar.sendPacket(SystemMsg.YOU_ARE_SHORT_OF_ACCUMULATED_POINTS);
						return;
					}
					if(!ingridient.getMantainIngredient())
						items.add(new ItemData(ingridientItemId, totalAmount, null));
				}
				else if(ingridientItemId == ItemTemplate.ITEM_ID_FAME)
				{
					if(activeChar.getFame() < totalAmount)
					{
						activeChar.sendPacket(SystemMsg.YOU_DONT_HAVE_ENOUGH_REPUTATION_TO_DO_THAT);
						return;
					}
					if(!ingridient.getMantainIngredient())
						items.add(new ItemData(ingridientItemId, totalAmount, null));
				}
				else
				{
					ItemTemplate template = ItemHolder.getInstance().getTemplate(ingridientItemId);

					if(!template.isStackable())
						for(int i = 0; i < ingridientItemCount * _amount; i++)
						{
							List<ItemInstance> list = inventory.getItemsByItemId(ingridientItemId);
							// Если энчант имеет значение - то ищем вещи с точно таким энчантом
							if(keepenchant)
							{
								ItemInstance itemToTake = null;
								for(ItemInstance item : list)
								{
									ItemData itmd = new ItemData(item.getItemId(), item.getCount(), item);
									if((item.getEnchantLevel() == ingridientEnchant || !item.getTemplate().isEquipment()) && !items.contains(itmd) && item.canBeExchanged(activeChar))
									{
										itemToTake = item;
										break;
									}
								}

								if(itemToTake == null)
								{
									activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ENOUGH_REQUIRED_ITEMS);
									return;
								}

								if(!ingridient.getMantainIngredient())
									items.add(new ItemData(itemToTake.getItemId(), 1, itemToTake));
							}
							// Если энчант не обрабатывается берется вещь с наименьшим энчантом
							else
							{
								ItemInstance itemToTake = null;
								for(ItemInstance item : list)
									if(!items.contains(new ItemData(item.getItemId(), item.getCount(), item)) && (itemToTake == null || item.getEnchantLevel() < itemToTake.getEnchantLevel()) && !item.isShadowItem() && !item.isTemporalItem() && (!item.isAugmented() || Config.ALT_ALLOW_DROP_AUGMENTED) && ItemFunctions.checkIfCanDiscard(activeChar, item))
									{
										itemToTake = item;
										if(itemToTake.getEnchantLevel() == 0)
											break;
									}

								if(itemToTake == null)
								{
									activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ENOUGH_REQUIRED_ITEMS);
									return;
								}

								if(!ingridient.getMantainIngredient())
									items.add(new ItemData(itemToTake.getItemId(), 1, itemToTake));
							}
						}
					else
					{
						if(ingridientItemId == 57)
							totalPrice = SafeMath.addAndCheck(totalPrice, SafeMath.mulAndCheck(ingridientItemCount, _amount));
						ItemInstance item = inventory.getItemByItemId(ingridientItemId);

						if(item == null || item.getCount() < totalAmount)
						{
							activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ENOUGH_REQUIRED_ITEMS);
							return;
						}

						if(!ingridient.getMantainIngredient())
							items.add(new ItemData(item.getItemId(), totalAmount, item));
					}
				}

				if(activeChar.getAdena() < totalPrice)
				{
					activeChar.sendPacket(SystemMsg.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
					return;
				}
			}

			int enchantLevel = 0;
			ItemAttributes attributes = null;
			int variationStoneId = 0;
			int variation1Id = 0;
			int variation2Id = 0;
			for(ItemData id : items)
			{
				long count = id.getCount();
				if(count > 0)
					if(id.getId() == ItemTemplate.ITEM_ID_CLAN_REPUTATION_SCORE)
					{
						activeChar.getClan().incReputation((int) -count, false, "MultiSell");
						activeChar.sendPacket(new SystemMessagePacket(SystemMsg.S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_THE_CLANS_REPUTATION).addLong(count));
					}
					else if(id.getId() == ItemTemplate.ITEM_ID_PC_BANG_POINTS)
						activeChar.reducePcBangPoints((int) count);
					else if(id.getId() == ItemTemplate.ITEM_ID_FAME)
					{
						activeChar.setFame(activeChar.getFame() - (int) count, "MultiSell");
						activeChar.sendPacket(new SystemMessagePacket(SystemMsg.S2_S1_HAS_DISAPPEARED).addLong(count).addString("Fame"));
					}
					else
					{
						if(inventory.destroyItem(id.getItem(), count))
						{
							if(keepenchant && id.getItem().canBeEnchanted())
							{
								enchantLevel = id.getItem().getEnchantLevel();
								attributes = id.getItem().getAttributes();
								variationStoneId = id.getItem().getVariationStoneId();
								variation1Id = id.getItem().getVariation1Id();
								variation2Id = id.getItem().getVariation2Id();
							}
							else if(!Config.RETAIL_MULTISELL_ENCHANT_TRANSFER && id.getItem().canBeEnchanted())
							{
								if(id.getItem().getAttributes() != null)
									attributes = id.getItem().getAttributes();
								if(id.getItem().getEnchantLevel() > 0)
									enchantLevel = id.getItem().getEnchantLevel();
								if(id.getItem().getVariationStoneId() > 0)
									variationStoneId = id.getItem().getVariationStoneId();
								if(id.getItem().getVariation1Id() > 0)
									variation1Id = id.getItem().getVariation1Id();
								if(id.getItem().getVariation2Id() > 0)
									variation2Id = id.getItem().getVariation2Id();
							}

							activeChar.sendPacket(SystemMessagePacket.removeItems(id.getId(), count));
							continue;
						}

						//TODO audit
						return;
					}
			}

			if(tax > 0 && !notax)
			{
				if(castle != null)
				{
					if(merchant != null && merchant.getReflection() == ReflectionManager.DEFAULT)
						castle.addToTreasury(tax, true, false);
				}
			}

			List<MultiSellIngredient> products = entry.getProduction();
			if(list1.getType() == MultisellType.CHANCED)
			{
				int chancesAmount = 0;
				List<MultiSellIngredient> productsTemp = new ArrayList<MultiSellIngredient>();
				for(MultiSellIngredient in : products)
				{
					int chance = in.getChance();
					if(chance <= 0)
						continue;

					chancesAmount += chance;
					productsTemp.add(in);
				}

				if(Rnd.chance(chancesAmount))
				{
					double chanceMod = (100. - chancesAmount) / productsTemp.size();
					List<MultiSellIngredient> successProducts = new ArrayList<MultiSellIngredient>();
					int tryCount = 0;
					while(successProducts.isEmpty())
					{
						tryCount++;
						for(MultiSellIngredient in : productsTemp)
						{
							if((tryCount % 10) == 0) //Немного теряем шанс, но зато зацикливания будут меньше.
								chanceMod += 1.;
							if(Rnd.chance(in.getChance() + chanceMod))
								successProducts.add(in);
						}
					}
					MultiSellIngredient[] productionsArray = successProducts.toArray(new MultiSellIngredient[successProducts.size()]);
					products = new ArrayList<MultiSellIngredient>(1);
					products.add(productionsArray[Rnd.get(productionsArray.length)]);
				}
			}

			for(MultiSellIngredient in : products)
			{
				if(in.getItemId() <= 0)
				{
					if(in.getItemId() == ItemTemplate.ITEM_ID_CLAN_REPUTATION_SCORE)
					{
						activeChar.getClan().incReputation((int) (in.getItemCount() * _amount), false, "MultiSell");
						activeChar.sendPacket(new SystemMessage(SystemMessage.YOUR_CLAN_HAS_ADDED_1S_POINTS_TO_ITS_CLAN_REPUTATION_SCORE).addNumber(in.getItemCount() * _amount));
					}
					else if(in.getItemId() == ItemTemplate.ITEM_ID_PC_BANG_POINTS)
						activeChar.addPcBangPoints((int) (in.getItemCount() * _amount), false);
					else if(in.getItemId() == ItemTemplate.ITEM_ID_FAME)
						activeChar.setFame(activeChar.getFame() + (int) (in.getItemCount() * _amount), "MultiSell");
				}
				else if(ItemHolder.getInstance().getTemplate(in.getItemId()).isStackable())
				{
					long total = SafeMath.mulAndLimit(in.getItemCount(), _amount);
					ItemFunctions.addItem(activeChar, in.getItemId(), total, true);
					Log.LogMultisell("Character " + activeChar.getName() + " bought " + total + " of " + in.getItemId() + " ingridients: ID: "+in.getItemId()+" count: " + in.getItemCount());
				}
				else
				{
					for(int i = 0; i < _amount; i++)
					{
						ItemInstance product = ItemFunctions.createItem(in.getItemId());

						if(keepenchant)
						{
							if(product.canBeEnchanted())
							{
								product.setEnchantLevel(enchantLevel);
								if(attributes != null)
									product.setAttributes(attributes.clone());
								if(variationStoneId > 0)
									product.setVariationStoneId(variationStoneId);
								if(variation1Id != 0)
									product.setVariation1Id(variation1Id);
								if(variation2Id != 0)
									product.setVariation2Id(variation2Id);
							}
						}
						else
						{
							product.setEnchantLevel(in.getItemEnchant());
							product.setAttributes(in.getItemAttributes().clone());
						}

						activeChar.sendPacket(SystemMessagePacket.obtainItems(product));
						inventory.addItem(product);
						Log.LogMultisell("Character " + activeChar.getName() + " bought 1 of " + product.getItemId() + " igridients: Id: "+in.getItemId()+" count: " + in.getItemCount() +"");
					}
				}
			}
		}
		catch(ArithmeticException ae)
		{
			//TODO audit
			activeChar.sendPacket(SystemMsg.YOU_HAVE_EXCEEDED_THE_QUANTITY_THAT_CAN_BE_INPUTTED);
			return;
		}
		finally
		{
			inventory.writeUnlock();
		}

		activeChar.sendChanges();

		if(!list1.isShowAll()) // Если показывается только то, на что хватает материалов обновить окно у игрока
			MultiSellHolder.getInstance().SeparateAndSend(list1, activeChar, castle == null ? 0 : castle.getTaxRate());

		activeChar.setLastMultisellBuyTime(System.currentTimeMillis());
	}
}