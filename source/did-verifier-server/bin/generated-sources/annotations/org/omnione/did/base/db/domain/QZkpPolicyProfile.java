package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QZkpPolicyProfile is a Querydsl query type for ZkpPolicyProfile
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QZkpPolicyProfile extends EntityPathBase<ZkpPolicyProfile> {

    private static final long serialVersionUID = -1017571435L;

    public static final QZkpPolicyProfile zkpPolicyProfile = new QZkpPolicyProfile("zkpPolicyProfile");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath description = createString("description");

    public final StringPath encoding = createString("encoding");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath language = createString("language");

    public final StringPath profileId = createString("profileId");

    public final StringPath title = createString("title");

    public final EnumPath<org.omnione.did.base.datamodel.enums.ProfileType> type = createEnum("type", org.omnione.did.base.datamodel.enums.ProfileType.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public final NumberPath<Long> zkpProofRequestId = createNumber("zkpProofRequestId", Long.class);

    public QZkpPolicyProfile(String variable) {
        super(ZkpPolicyProfile.class, forVariable(variable));
    }

    public QZkpPolicyProfile(Path<? extends ZkpPolicyProfile> path) {
        super(path.getType(), path.getMetadata());
    }

    public QZkpPolicyProfile(PathMetadata metadata) {
        super(ZkpPolicyProfile.class, metadata);
    }

}

