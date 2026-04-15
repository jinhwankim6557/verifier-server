package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QApiKey is a Querydsl query type for ApiKey
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QApiKey extends EntityPathBase<ApiKey> {

    private static final long serialVersionUID = -228730174L;

    public static final QApiKey apiKey1 = new QApiKey("apiKey1");

    public final QBaseEntity _super = new QBaseEntity(this);

    public final StringPath apiKey = createString("apiKey");

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath createdBy = createString("createdBy");

    public final StringPath description = createString("description");

    public final DateTimePath<java.time.LocalDateTime> expiresAt = createDateTime("expiresAt", java.time.LocalDateTime.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final BooleanPath isActive = createBoolean("isActive");

    public final DateTimePath<java.time.LocalDateTime> lastUsedAt = createDateTime("lastUsedAt", java.time.LocalDateTime.class);

    public final StringPath name = createString("name");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QApiKey(String variable) {
        super(ApiKey.class, forVariable(variable));
    }

    public QApiKey(Path<? extends ApiKey> path) {
        super(path.getType(), path.getMetadata());
    }

    public QApiKey(PathMetadata metadata) {
        super(ApiKey.class, metadata);
    }

}

