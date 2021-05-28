package com.generalbytes.batm.server.extensions.extra.common;

import com.generalbytes.batm.server.extensions.IGeneratesNewDepositCryptoAddress;
import com.generalbytes.batm.server.extensions.IQueryableWallet;
import com.generalbytes.batm.server.extensions.payment.IPaymentRequestSpecification;
import com.generalbytes.batm.server.extensions.payment.PaymentRequest;
import com.generalbytes.batm.server.extensions.payment.ReceivedAmount;

import java.math.BigDecimal;

public abstract class QueryableWalletPaymentSupport extends PollingPaymentSupport {

    @Override
    public PaymentRequest createPaymentRequest(IPaymentRequestSpecification spec) {
        if (!(spec.getWallet() instanceof IGeneratesNewDepositCryptoAddress)) {
            throw new IllegalArgumentException("Wallet '" + spec.getWallet().getClass() + "' does not implement " + IGeneratesNewDepositCryptoAddress.class.getSimpleName());
        }
        if (!(spec.getWallet() instanceof IQueryableWallet)) {
            throw new IllegalArgumentException("Wallet '" + spec.getWallet().getClass() + "' does not implement " + IQueryableWallet.class.getSimpleName());
        }
        return super.createPaymentRequest(spec);
    }

    @Override
    public void poll(PaymentRequest request) {
        try {
            IQueryableWallet wallet = (IQueryableWallet) request.getWallet();
            ReceivedAmount receivedAmount = wallet.getReceivedAmount(request.getAddress(), request.getCryptoCurrency());

            BigDecimal totalReceived = receivedAmount.getTotalAmountReceived();
            int confirmations = receivedAmount.getConfirmations();

            if (totalReceived.compareTo(BigDecimal.ZERO) == 0) {
                return;
            }

            if (totalReceived.compareTo(request.getAmount()) != 0) {
                log.info("Received amount ({}) does not match the requested amount ({}), {}", totalReceived, request.getAmount(), request);
                // stop future polling
                setState(request, PaymentRequest.STATE_TRANSACTION_INVALID);
                return;
            }

            // correct amount received

            if (request.getState() == PaymentRequest.STATE_NEW) {
                log.info("Received: {}, amounts matches. {}", totalReceived, request);
                request.setTxValue(totalReceived);
                setState(request, PaymentRequest.STATE_SEEN_TRANSACTION);
            }

            if (confirmations > 0) {
                if (request.getState() == PaymentRequest.STATE_SEEN_TRANSACTION) {
                    log.info("Transaction confirmed. {}", request);
                    setState(request, PaymentRequest.STATE_SEEN_IN_BLOCK_CHAIN);
                }
                updateNumberOfConfirmations(request, confirmations);
            }


        } catch (Exception e) {
            log.error("", e);
        }
    }
}
