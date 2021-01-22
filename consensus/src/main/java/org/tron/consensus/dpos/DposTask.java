package org.tron.consensus.dpos;

import static org.tron.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;

import com.google.protobuf.ByteString;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.tron.common.parameter.CommonParameter;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.consensus.ConsensusDelegate;
import org.tron.consensus.base.Param.Miner;
import org.tron.consensus.base.State;
import org.tron.core.capsule.BlockCapsule;
import org.tron.protos.Protocol.BlockHeader;

@Slf4j(topic = "consensus")
@Component
public class DposTask {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Autowired
  private DposSlot dposSlot;

  @Autowired
  private StateManager stateManager;

  @Setter
  private DposService dposService;

  private Thread produceThread;

  private volatile boolean isRunning = true;

  public void init() {

    if (!dposService.isEnable() || StringUtils.isEmpty(dposService.getMiners())) {
      return;
    }

    Runnable runnable = () -> {
      while (isRunning) {
        try {
          if (dposService.isNeedSyncCheck()) {
            Thread.sleep(1000);
            dposService.setNeedSyncCheck(dposSlot.getTime(1) < System.currentTimeMillis());
          } else {
            long time =
                BLOCK_PRODUCED_INTERVAL - System.currentTimeMillis() % BLOCK_PRODUCED_INTERVAL;
            Thread.sleep(time);
            State state = produceBlock();
            if (!State.OK.equals(state)) {
              logger.info("Produce block failed: {}", state);
            }
          }
        } catch (Throwable throwable) {
          logger.error("Produce block error.", throwable);
        }
      }
    };
    produceThread = new Thread(runnable, "DPosMiner");
    //有安全隐患
    produceThread.start();
    logger.info("DPoS task started.");
  }

  public void stop() {
    isRunning = false;
    if (produceThread != null) {
      produceThread.interrupt();
    }
    logger.info("DPoS task stopped.");
  }

  //产块任务
  private State produceBlock() {

    State state = stateManager.getState();
    if (!State.OK.equals(state)) {
      return state;
    }

    synchronized (dposService.getBlockHandle().getLock()) {
      //检测是否到了一个 slot 时间
      long slot = dposSlot.getSlot(System.currentTimeMillis() + 50);
      if (slot == 0) {
        return State.NOT_TIME_YET;
      }
      //拿到在线的 SR 地址，实际作用是：判断是否轮到自己产块，27个 SR 每3秒产一个块，当前判断是否为自己的那3秒
      ByteString pWitness = dposSlot.getScheduledWitness(slot);
      //如果拿不到，步是还没轮到自己产块
      Miner miner = dposService.getMiners().get(pWitness);
      if (miner == null) {
        return State.NOT_MY_TURN;
      }

      long pTime = dposSlot.getTime(slot);
      long timeout =
          pTime + BLOCK_PRODUCED_INTERVAL / 2 * dposService.getBlockProduceTimeoutPercent() / 100;

      //产块
      BlockCapsule blockCapsule = dposService.getBlockHandle().produce(miner, pTime, timeout);
      if (blockCapsule == null) {
        return State.PRODUCE_BLOCK_FAILED;
      }

      BlockHeader.raw raw = blockCapsule.getInstance().getBlockHeader().getRawData();
      logger.info("Produce block successfully, num: {}, time: {}, witness: {}, ID:{}, parentID:{}",
          raw.getNumber(),
          new DateTime(raw.getTimestamp()),
          ByteArray.toHexString(raw.getWitnessAddress().toByteArray()),
          new Sha256Hash(raw.getNumber(), Sha256Hash.of(CommonParameter
              .getInstance().isECKeyCryptoEngine(), raw.toByteArray())),
          ByteArray.toHexString(raw.getParentHash().toByteArray()));
    }

    return State.OK;
  }

}
