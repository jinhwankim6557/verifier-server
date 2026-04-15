package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QSubTransaction is a Querydsl query type for SubTransaction
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QSubTransaction extends EntityPathBase<SubTransaction> {

    private static final long serialVersionUID = 180601947L;

    public static final QSubTransaction subTransaction = new QSubTransaction("subTransaction");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final EnumPath<org.omnione.did.base.db.constant.SubTransactionStatus> status = createEnum("status", org.omnione.did.base.db.constant.SubTransactionStatus.class);

    public final NumberPath<Integer> step = createNumber("step", Integer.class);

    public final NumberPath<Long> transactionId = createNumber("transactionId", Long.class);

    public final EnumPath<org.omnione.did.base.db.constant.SubTransactionType> type = createEnum("type", org.omnione.did.base.db.constant.SubTransactionType.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QSubTransaction(String variable) {
        super(SubTransaction.class, forVariable(variable));
    }

    public QSubTransaction(Path<? extends SubTransaction> path) {
        super(path.getType(), path.getMetadata());
    }

    public QSubTransaction(PathMetadata metadata) {
        super(SubTransaction.class, metadata);
    }

}

