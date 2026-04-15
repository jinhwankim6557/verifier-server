package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QVpFilter is a Querydsl query type for VpFilter
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QVpFilter extends EntityPathBase<VpFilter> {

    private static final long serialVersionUID = 498805871L;

    public static final QVpFilter vpFilter = new QVpFilter("vpFilter");

    public final QBaseEntity _super = new QBaseEntity(this);

    public final ListPath<String, StringPath> allowedIssuers = this.<String, StringPath>createList("allowedIssuers", String.class, StringPath.class, PathInits.DIRECT2);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final ListPath<String, StringPath> displayClaims = this.<String, StringPath>createList("displayClaims", String.class, StringPath.class, PathInits.DIRECT2);

    public final NumberPath<Long> filterId = createNumber("filterId", Long.class);

    public final StringPath id = createString("id");

    public final BooleanPath present_all = createBoolean("present_all");

    public final ListPath<String, StringPath> requiredClaims = this.<String, StringPath>createList("requiredClaims", String.class, StringPath.class, PathInits.DIRECT2);

    public final StringPath title = createString("title");

    public final StringPath type = createString("type");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public final StringPath value = createString("value");

    public QVpFilter(String variable) {
        super(VpFilter.class, forVariable(variable));
    }

    public QVpFilter(Path<? extends VpFilter> path) {
        super(path.getType(), path.getMetadata());
    }

    public QVpFilter(PathMetadata metadata) {
        super(VpFilter.class, metadata);
    }

}

