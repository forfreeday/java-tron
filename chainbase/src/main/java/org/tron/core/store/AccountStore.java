package org.tron.core.store;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.typesafe.config.ConfigObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tron.common.utils.Commons;
import org.tron.core.capsule.AccountBalanceCapsule;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.db.TronStoreWithRevoking;
import org.tron.core.db.accountstate.AccountStateCallBackUtils;
import org.tron.core.exception.BadItemException;

@Slf4j(topic = "DB")
@Component
public class AccountStore extends TronStoreWithRevoking<AccountCapsule> {

  private static Map<String, byte[]> assertsAddress = new HashMap<>(); // key = name , value = address

  @Autowired
  private AccountStateCallBackUtils accountStateCallBackUtils;

  @Getter
  @Setter
  private AccountBalanceStore accountBalanceStore;

  @Autowired
  private AccountStore(@Value("account") String dbName) {
    super(dbName);
  }

  public static void setAccount(com.typesafe.config.Config config) {
    List list = config.getObjectList("genesis.block.assets");
    for (int i = 0; i < list.size(); i++) {
      ConfigObject obj = (ConfigObject) list.get(i);
      String accountName = obj.get("accountName").unwrapped().toString();
      byte[] address = Commons.decodeFromBase58Check(obj.get("address").unwrapped().toString());
      assertsAddress.put(accountName, address);
    }
  }

  @Override
  public AccountCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new AccountCapsule(value, accountBalanceStore);
  }

  @Override
  public void put(byte[] key, AccountCapsule item) {
    super.put(key, item);
    accountStateCallBackUtils.accountCallBack(key, item);
    accountBalanceStore.put(key, item.getAccountBalanceCapsule());
  }

  /**
   * Max TRX account.
   */
  public AccountCapsule getSun() {
    return getUnchecked(assertsAddress.get("Sun"));
  }

  /**
   * Min TRX account.
   */
  public AccountCapsule getBlackhole() {
    return getUnchecked(assertsAddress.get("Blackhole"));
  }

  /**
   * Get foundation account info.
   */
  public AccountCapsule getZion() {
    return getUnchecked(assertsAddress.get("Zion"));
  }

  @Override
  public void close() {
    super.close();
  }

  @Override
  public Iterator<Map.Entry<byte[], AccountCapsule>> iterator() {
    long start = System.currentTimeMillis();
    Iterator<Map.Entry<byte[], AccountCapsule>> transform = Iterators.transform(revokingDB.iterator(), e -> {
      try {
        logger.info("Iterator: {}", System.currentTimeMillis() - start);
        return Maps.immutableEntry(e.getKey(), of(e.getValue()));
      } catch (BadItemException e1) {
        throw new RuntimeException(e1);
      }
    });
    return transform;
  }

  public Iterator<Map.Entry<byte[], AccountCapsule>> iterator2(AccountBalanceCapsule accountBalanceCapsule) {
    Iterator<Map.Entry<byte[], AccountCapsule>> transform = Iterators.transform(revokingDB.iterator(), e -> {
      try {
        Map.Entry<byte[], AccountCapsule> accountCapsuleEntry = Maps.immutableEntry(e.getKey(), of(e.getValue()));





        return accountCapsuleEntry;
      } catch (BadItemException e1) {
        throw new RuntimeException(e1);
      }
    });

    return transform;
  }

}
