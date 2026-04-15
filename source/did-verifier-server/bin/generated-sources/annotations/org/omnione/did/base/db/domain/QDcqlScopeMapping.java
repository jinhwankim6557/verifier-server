package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QDcqlScopeMapping is a Querydsl query type for DcqlScopeMapping
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QDcqlScopeMapping extends EntityPathBase<DcqlScopeMapping> {

    private static final long serialVersionUID = -559469455L;

    public static final QDcqlScopeMapping dcqlScopeMapping = new QDcqlScopeMapping("dcqlScopeMapping");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath dcqlQuery = createString("dcqlQuery");

    public final StringPath description = createString("description");

    public final BooleanPath enabled = createBoolean("enabled");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath scope = createString("scope");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QDcqlScopeMapping(String variable) {
        super(DcqlScopeMapping.class, forVariable(variable));
    }

    public QDcqlScopeMapping(Path<? extends DcqlScopeMapping> path) {
        super(path.getType(), path.getMetadata());
    }

    public QDcqlScopeMapping(PathMetadata metadata) {
        super(DcqlScopeMapping.class, metadata);
    }

}

