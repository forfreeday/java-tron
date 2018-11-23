package org.tron.stresstest.dispatch.creator.contract;

import com.google.protobuf.ByteString;
import org.spongycastle.util.encoders.Hex;
import org.tron.common.crypto.ECKey;
import org.tron.core.Wallet;
import org.tron.protos.Contract;
import org.tron.stresstest.AbiUtil;
import org.tron.stresstest.dispatch.GoodCaseTransactonCreator;
import org.tron.stresstest.dispatch.TransactionFactory;
import org.tron.stresstest.dispatch.creator.CreatorCounter;
import org.tron.stresstest.dispatch.creator.transfer.AbstractTransferTransactionCreator;
import org.tron.common.utils.ByteArray;
import org.tron.protos.Contract.TriggerSmartContract;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.stresstest.exception.EncodingException;

public class TriggerFibonacciCreator extends AbstractTransferTransactionCreator implements GoodCaseTransactonCreator {
  @Override
  protected Protocol.Transaction create() {
    TransactionFactory.context.getBean(CreatorCounter.class).put(this.getClass().getName());

    String param = "2100";

    TriggerSmartContract contract = null;
    try {
      contract = triggerCallContract(ownerAddress.toByteArray(), Wallet
          .decodeFromBase58Check("TPj2gCtwRyGHaMmYDRUCuwp1bSvowrjunk"), 0L, Hex
          .decode(AbiUtil.parseMethod("add2(uint256)", param, false)));
    } catch (EncodingException e) {
      e.printStackTrace();
    }

    Protocol.Transaction transaction = createTransaction(contract, ContractType.TriggerSmartContract);

    transaction = transaction.toBuilder().setRawData(transaction.getRawData().toBuilder().setFeeLimit(1000000000).build()).build();

    transaction = sign(transaction, ECKey.fromPrivate(ByteArray.fromHexString(privateKey)));
    return transaction;
  }
}
