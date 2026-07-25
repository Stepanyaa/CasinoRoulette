package ru.stepanyaa.casinoRoulette;
import java.util.UUID;
public class PlayerStats { public final UUID uuid; public String name; public long chips,wins,losses,totalWon,totalLost,rounds,bets,biggestWin,biggestLoss,dailyUses,casesOpened,wheelSpins,chipsBought,dailyTotalWon,mostValuableDailyReward; public String lastDailyReward=""; public long lastDailyUse=0; public PlayerStats(UUID uuid){this.uuid=uuid;} }
