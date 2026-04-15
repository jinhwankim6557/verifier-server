package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QZkpProofRequest is a Querydsl query type for ZkpProofRequest
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QZkpProofRequest extends EntityPathBase<ZkpProofRequest> {

    private static final long serialVersionUID = 1880042861L;

    public static final QZkpProofRequest zkpProofRequest = new QZkpProofRequest("zkpProofRequest");

    public final QBaseEntity _super = new QBaseEntity(this);

    public final EnumPath<org.omnione.did.base.datamodel.enums.SymmetricCipherType> cipher = createEnum("cipher", org.omnione.did.base.datamodel.enums.SymmetricCipherType.class);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final EnumPath<org.omnione.did.base.datamodel.enums.EccCurveType> curve = createEnum("curve", org.omnione.did.base.datamodel.enums.EccCurveType.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath name = createString("name");

    public final EnumPath<org.omnione.did.base.datamodel.enums.SymmetricPaddingType> padding = createEnum("padding", org.omnione.did.base.datamodel.enums.SymmetricPaddingType.class);

    public final StringPath requestedAttributes = createString("requestedAttributes");

    public final StringPath requestedPredicates = createString("requestedPredicates");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public final StringPath version = createString("version");

    public QZkpProofRequest(String variable) {
        super(ZkpProofRequest.class, forVariable(variable));
    }

    public QZkpProofRequest(Path<? extends ZkpProofRequest> path) {
        super(path.getType(), path.getMetadata());
    }

    public QZkpProofRequest(PathMetadata metadata) {
        super(ZkpProofRequest.class, metadata);
    }

}

