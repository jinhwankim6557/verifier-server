package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QOid4vpConfig is a Querydsl query type for Oid4vpConfig
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOid4vpConfig extends EntityPathBase<Oid4vpConfig> {

    private static final long serialVersionUID = -504590493L;

    public static final QOid4vpConfig oid4vpConfig = new QOid4vpConfig("oid4vpConfig");

    public final QBaseEntity _super = new QBaseEntity(this);

    public final StringPath config = createString("config");

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath type = createString("type");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QOid4vpConfig(String variable) {
        super(Oid4vpConfig.class, forVariable(variable));
    }

    public QOid4vpConfig(Path<? extends Oid4vpConfig> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOid4vpConfig(PathMetadata metadata) {
        super(Oid4vpConfig.class, metadata);
    }

}

