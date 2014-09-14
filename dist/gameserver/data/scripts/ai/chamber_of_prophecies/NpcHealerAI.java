package ai.chamber_of_prophecies;

import java.util.List;

import l2s.gameserver.ai.CtrlIntention;
import l2s.gameserver.ai.Priest;
import l2s.gameserver.geodata.GeoEngine;
import l2s.gameserver.model.Creature;
import l2s.gameserver.model.Player;
import l2s.gameserver.model.Skill;
import l2s.gameserver.model.World;
import l2s.gameserver.model.instances.DecoyInstance;
import l2s.gameserver.model.instances.NpcInstance;
import l2s.gameserver.network.l2.s2c.MagicSkillUse;
import l2s.gameserver.stats.Stats;
/**
 * @author Hien Son
 */
public class NpcHealerAI extends Priest
{
	Creature attackTarget = null;
	Player healTarget = null;
	
	public NpcHealerAI(NpcInstance actor)
	{
		super(actor);
	}

	@Override
	protected boolean thinkActive()
	{
		double chance = Math.random();
		NpcInstance actor = getActor();
		
		if(chance*100 < getRateHEAL())
		{
			System.out.println("Ferin heal chance triggered");
			for(Player player : World.getAroundPlayers(actor, 2000, 300))
			{
				System.out.println("player " + player.getName() + " isDead " + player.isDead() + " isAlikeDead " + player.isAlikeDead() + " isVisible " + player.isVisible());
				if(player != null && !player.isDead() && !player.isAlikeDead() && player.isVisible())
				{
					Skill skill = null;
					System.out.println("checkHealTarget " + checkHealTarget(healTarget));
					double distance = actor.getDistance(player);
					if(checkHealTarget(player) == 1)
					{
						Skill[] healSkillList = selectUsableSkills(player, distance, actor.getTemplate().getHealSkills());
						int randomIndex = (int)Math.random()*healSkillList.length;
						skill = healSkillList[randomIndex];
						
					}
					else if(checkHealTarget(player) == 2)
					{
						Skill[] rechargeSkillList = selectUsableSkills(player, distance, actor.getTemplate().getManaHealSkills());
						int randomIndex = (int)Math.random()*rechargeSkillList.length;
						skill = rechargeSkillList[randomIndex];
					}
					
					if(skill == null)
						continue;
					
					if(distance > skill.getCastRange())
					{
						tryMoveToTarget(player, skill.getCastRange());
						return false;
					}
					
					if(canUseSkill(skill, player, distance))
					{
						System.out.println("skill " + skill.getName());
						actor.doCast(skill, player, true);
						actor.broadcastPacket(new MagicSkillUse(actor, player, skill.getId(), 1, 0, 0, false));
					}
					else
						System.out.println("Skill is null");
					
				}
			}
			return false;
		}
		else
			return startAttack();
	}
	

	
	protected boolean canUseSkill(Skill skill, Creature target, double distance, boolean override)
	{

		NpcInstance actor = getActor();
		
		if ((skill == null) || skill.isNotUsedByAI())
		{
			return false;
		}
		
		if(!skill.checkCondition(actor, target, true, false, false))
		{
			return false;
		}
				
		if ((skill.getTargetType() == Skill.SkillTargetType.TARGET_SELF) && (target != actor))
		{
			return false;
		}
		
		int castRange = skill.getAOECastRange();
		if ((castRange <= 200) && (distance > 200))
		{
			return false;
		}
		
		if (actor.isSkillDisabled(skill) || actor.isMuted(skill) || actor.isUnActiveSkill(skill.getId()))
		{
			return false;
		}
		
		double mpConsume2 = skill.getMpConsume2();
		if (skill.isMagic())
		{
			mpConsume2 = actor.calcStat(Stats.MP_MAGIC_SKILL_CONSUME, mpConsume2, target, skill);
		}
		else
		{
			mpConsume2 = actor.calcStat(Stats.MP_PHYSICAL_SKILL_CONSUME, mpConsume2, target, skill);
		}
		if (actor.getCurrentMp() < mpConsume2)
		{
			return false;
		}
		
		if (!override && target.getEffectList().containsEffects(skill))
		{
			return false;
		}
		
		return true;
	}
	
	
	private int checkHealTarget(Player player)
	{
		if(player == null)
			return 0;
		
		if(player.getCurrentHpPercents() < 80)
			return 1;
		
		if(player.getCurrentMpPercents() < 80)
			return 2;
		
		return 0;
	}

	private boolean startAttack()
	{
		NpcInstance actor = getActor();
		if(attackTarget == null || attackTarget.isDead())
		{
			List<NpcInstance> around = actor.getAroundNpc(2000, 150);
			if(around != null && !around.isEmpty())
			{
				for(NpcInstance npc : around)
				{
					if(checkattackTarget(npc))
					{
						if(attackTarget == null || actor.getDistance3D(npc) < actor.getDistance3D(attackTarget))
							attackTarget = npc;
						
					}
				}
			}
		}

		if(attackTarget != null && !actor.isAttackingNow() && !actor.isCastingNow() && !attackTarget.isDead() && GeoEngine.canSeeTarget(actor, attackTarget, false) && attackTarget.isVisible())
		{
			actor.getAggroList().addDamageHate(attackTarget, 10, 10);
			actor.setAggressionTarget(attackTarget);
			actor.setRunning();
			setIntention(CtrlIntention.AI_INTENTION_ATTACK, attackTarget);
			return true;
		}

		if(attackTarget != null && (!attackTarget.isVisible() || attackTarget.isDead() || !GeoEngine.canSeeTarget(actor, attackTarget, false)))
		{
			attackTarget = null;
			return false;
		}
		
		return false;
	}
	
	@Override
	protected void thinkAttack()
	{
		NpcInstance actor = getActor();
		if(actor.isDead())
			return;

		if(doTask() && !actor.isAttackingNow() && !actor.isCastingNow())
		{
			if(!createNewTask())
			{
				if(System.currentTimeMillis() > getAttackTimeout() && !(actor instanceof DecoyInstance))
					returnHome();
			}
		}
	}

	@Override
	protected boolean isGlobalAggro()
	{
		return true;
	}
	
	private boolean checkattackTarget(NpcInstance attackTarget)
	{
		if(attackTarget == null)
			return false;
		
		if (((NpcInstance) attackTarget).isInFaction(getActor()))
			return false;
			
		return true;
	}

	@Override
	public int getMaxAttackTimeout()
	{
		return 0;
	}

	@Override
	protected boolean randomWalk()
	{
		return true;
	}
	

	@Override
	public int getRateDOT()
	{
		return 0;
	}

	@Override
	public int getRateDEBUFF()
	{
		return 30;
	}

	@Override
	public int getRateDAM()
	{
		return 30;
	}

	@Override
	public int getRateSTUN()
	{
		return 30;
	}

	@Override
	public int getRateBUFF()
	{
		return 90;
	}

	@Override
	public int getRateHEAL()
	{
		return 90;
	}
}