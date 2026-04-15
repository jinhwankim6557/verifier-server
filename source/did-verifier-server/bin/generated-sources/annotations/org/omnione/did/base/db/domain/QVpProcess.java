package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QVpProcess is a Querydsl query type for VpProcess
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QVpProcess extends EntityPathBase<VpProcess> {

    private static final long serialVersionUID = -1171858344L;

    public static final QVpProcess vpProcess = new QVpProcess("vpProcess");

    public final QBaseEntity _super = new QBaseEntity(this);

    public final NumberPath<Integer> authType = createNumber("authType", Integer.class);

    public final EnumPath<org.omnione.did.base.datamodel.enums.SymmetricCipherType> cipher = createEnum("cipher", org.omnione.did.base.datamodel.enums.SymmetricCipherType.class);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final EnumPath<org.omnione.did.base.datamodel.enums.EccCurveType> curve = createEnum("curve", org.omnione.did.base.datamodel.enums.EccCurveType.class);

    public final ListPath<String, StringPath> endpoints = this.<String, StringPath>createList("endpoints", String.class, StringPath.class, PathInits.DIRECT2);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final EnumPath<org.omnione.did.base.datamodel.enums.SymmetricPaddingType> padding = createEnum("padding", org.omnione.did.base.datamodel.enums.SymmetricPaddingType.class);

    public final StringPath title = createString("title");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QVpProcess(String variable) {
        super(VpProcess.class, forVariable(variable));
    }

    public QVpProcess(Path<? extends VpProcess> path) {
        super(path.getType(), path.getMetadata());
    }

    public QVpProcess(PathMetadata metadata) {
        super(VpProcess.class, metadata);
    }

}

