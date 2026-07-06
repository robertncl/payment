package io.paylab.api.ledger;

import java.io.Serializable;

public class PostingResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String entryId;
    /** true when this call found an existing entry for (paymentId, entryType) — replay, no new rows. */
    private boolean alreadyPosted;

    public PostingResult() {}

    public PostingResult(String entryId, boolean alreadyPosted) {
        this.entryId = entryId;
        this.alreadyPosted = alreadyPosted;
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public boolean isAlreadyPosted() {
        return alreadyPosted;
    }

    public void setAlreadyPosted(boolean alreadyPosted) {
        this.alreadyPosted = alreadyPosted;
    }
}
