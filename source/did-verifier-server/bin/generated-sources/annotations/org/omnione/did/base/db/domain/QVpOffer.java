package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QVpOffer is a Querydsl query type for VpOffer
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QVpOffer extends EntityPathBase<VpOffer> {

    private static final long serialVersionUID = -1361166715L;

    public static final QVpOffer vpOffer = new QVpOffer("vpOffer");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath device = createString("device");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath offerId = createString("offerId");

    public final StringPath offerType = createString("offerType");

    public final StringPath passcode = createString("passcode");

    public final StringPath payload = createString("payload");

    public final StringPath service = createString("service");

    public final NumberPath<Long> transactionId = createNumber("transactionId", Long.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public final DateTimePath<java.time.Instant> validUntil = createDateTime("validUntil", java.time.Instant.class);

    public final StringPath vpPolicyId = createString("vpPolicyId");

    public QVpOffer(String variable) {
        super(VpOffer.class, forVariable(variable));
    }

    public QVpOffer(Path<? extends VpOffer> path) {
        super(path.getType(), path.getMetadata());
    }

    public QVpOffer(PathMetadata metadata) {
        super(VpOffer.class, metadata);
    }

}

