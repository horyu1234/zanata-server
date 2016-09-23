import React, {PropTypes} from 'react'
import {Button} from 'react-bootstrap'
const Entry = ({
  locale,
  permission,
  handleDelete,
  ...props
}) => {
  /* eslint-disable react/jsx-no-bind */
  return (
    <tr>
      <td>
        <a href=''>
          <span>{locale.displayName}</span>
          {locale.enabledByDefault &&
            <span className='greentext badge Mstart(rq)'>
              DEFAULT
            </span>
          }
          {!locale.enabled &&
            <span className='dis badge Mstart(rq)'>
              DISABLED
            </span>
          }
        </a>
        <br />
        <span className='langcode'>
          {locale.localeId} [{locale.nativeName}]
        </span>
      </td>
      <td>
        <span>
          <i className='fa fa-user'></i> {locale.membersCount}
        </span>
      </td>
      {permission.canDeleteLocale &&
        <td>
          <Button bsSize='small' onClick={() => handleDelete(locale.localeId)}>
            <i className='fa fa-times'></i> Delete
          </Button>
        </td>
      }
    </tr>
  )
  /* eslint-disable react/jsx-no-bind */
}

Entry.propTypes = {
  locale: PropTypes.object.isRequired,
  permission: PropTypes.object.isRequired,
  handleDelete: PropTypes.func
}

export default Entry
