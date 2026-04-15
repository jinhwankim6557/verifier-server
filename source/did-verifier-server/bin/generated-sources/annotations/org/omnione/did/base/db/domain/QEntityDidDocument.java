package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QEntityDidDocument is a Querydsl query type for EntityDidDocument
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QEntityDidDocument extends EntityPathBase<EntityDidDocument> {

    private static final long serialVersionUID = -1419576870L;

    public static final QEntityDidDocument entityDidDocument = new QEntityDidDocument("entityDidDocument");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath didDocument = createString("didDocument");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QEntityDidDocument(String variable) {
        super(EntityDidDocument.class, forVariable(variable));
    }

    public QEntityDidDocument(Path<? extends EntityDidDocument> path) {
        super(path.getType(), path.getMetadata());
    }

    public QEntityDidDocument(PathMetadata metadata) {
        super(EntityDidDocument.class, metadata);
    }

}

