package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QTransaction is a Querydsl query type for Transaction
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QTransaction extends EntityPathBase<Transaction> {

    private static final long serialVersionUID = -1815824351L;

    public static final QTransaction transaction = new QTransaction("transaction");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final DateTimePath<java.time.Instant> expired_at = createDateTime("expired_at", java.time.Instant.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final EnumPath<org.omnione.did.base.db.constant.TransactionStatus> status = createEnum("status", org.omnione.did.base.db.constant.TransactionStatus.class);

    public final StringPath txId = createString("txId");

    public final EnumPath<org.omnione.did.base.db.constant.TransactionType> type = createEnum("type", org.omnione.did.base.db.constant.TransactionType.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QTransaction(String variable) {
        super(Transaction.class, forVariable(variable));
    }

    public QTransaction(Path<? extends Transaction> path) {
        super(path.getType(), path.getMetadata());
    }

    public QTransaction(PathMetadata metadata) {
        super(Transaction.class, metadata);
    }

}

