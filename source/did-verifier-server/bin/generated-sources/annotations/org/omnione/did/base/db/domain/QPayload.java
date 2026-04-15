package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QPayload is a Querydsl query type for Payload
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPayload extends EntityPathBase<Payload> {

    private static final long serialVersionUID = 1513283665L;

    public static final QPayload payload = new QPayload("payload");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath device = createString("device");

    public final StringPath endpoints = createString("endpoints");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath locked = createBoolean("locked");

    public final EnumPath<org.omnione.did.base.db.constant.ProfileMode> mode = createEnum("mode", org.omnione.did.base.db.constant.ProfileMode.class);

    public final EnumPath<org.omnione.did.base.datamodel.enums.OfferType> offerType = createEnum("offerType", org.omnione.did.base.datamodel.enums.OfferType.class);

    public final StringPath payloadId = createString("payloadId");

    public final StringPath service = createString("service");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public final NumberPath<Integer> validSecond = createNumber("validSecond", Integer.class);

    public QPayload(String variable) {
        super(Payload.class, forVariable(variable));
    }

    public QPayload(Path<? extends Payload> path) {
        super(path.getType(), path.getMetadata());
    }

    public QPayload(PathMetadata metadata) {
        super(Payload.class, metadata);
    }

}

