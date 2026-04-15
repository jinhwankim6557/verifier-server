package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QVerifierInfo is a Querydsl query type for VerifierInfo
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QVerifierInfo extends EntityPathBase<VerifierInfo> {

    private static final long serialVersionUID = 472145857L;

    public static final QVerifierInfo verifierInfo = new QVerifierInfo("verifierInfo");

    public final QBaseEntity _super = new QBaseEntity(this);

    public final StringPath certificateUrl = createString("certificateUrl");

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath did = createString("did");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath name = createString("name");

    public final StringPath serverUrl = createString("serverUrl");

    public final EnumPath<org.omnione.did.base.db.constant.VerifierStatus> status = createEnum("status", org.omnione.did.base.db.constant.VerifierStatus.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QVerifierInfo(String variable) {
        super(VerifierInfo.class, forVariable(variable));
    }

    public QVerifierInfo(Path<? extends VerifierInfo> path) {
        super(path.getType(), path.getMetadata());
    }

    public QVerifierInfo(PathMetadata metadata) {
        super(VerifierInfo.class, metadata);
    }

}

