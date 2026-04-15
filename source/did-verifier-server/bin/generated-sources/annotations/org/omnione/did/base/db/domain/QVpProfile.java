package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QVpProfile is a Querydsl query type for VpProfile
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QVpProfile extends EntityPathBase<VpProfile> {

    private static final long serialVersionUID = -1171765358L;

    public static final QVpProfile vpProfile1 = new QVpProfile("vpProfile1");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath profileId = createString("profileId");

    public final NumberPath<Long> transactionId = createNumber("transactionId", Long.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public final StringPath vpProfile = createString("vpProfile");

    public QVpProfile(String variable) {
        super(VpProfile.class, forVariable(variable));
    }

    public QVpProfile(Path<? extends VpProfile> path) {
        super(path.getType(), path.getMetadata());
    }

    public QVpProfile(PathMetadata metadata) {
        super(VpProfile.class, metadata);
    }

}

